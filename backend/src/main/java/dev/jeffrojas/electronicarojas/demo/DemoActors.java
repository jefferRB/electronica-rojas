package dev.jeffrojas.electronicarojas.demo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * The existing collaborators and branches the demo scenario reuses (it never creates, edits or
 * duplicates them). Read-only lookups; each person becomes the same {@link CurrentUser} principal a
 * login would produce, so every use case checks role and branch scope as for a real session.
 *
 * @param missing what the database lacks for the scenario (empty when everything is in place)
 */
record DemoActors(long tresRios, long sanPedro, CurrentUser jefferson, CurrentUser susan, CurrentUser maicol,
		CurrentUser julian, CurrentUser pedro, List<String> missing) {

	static final String TRES_RIOS = "SUC-001";

	static final String SAN_PEDRO = "SUC-002";

	private record Person(long id, String email, String fullName, Role role, Set<String> branches) {
	}

	static DemoActors resolve(JdbcClient jdbc, UserDetailsService accounts) {
		List<String> missing = new ArrayList<>();
		long tresRios = branch(jdbc, TRES_RIOS, missing);
		long sanPedro = branch(jdbc, SAN_PEDRO, missing);
		List<Person> people = jdbc.sql("""
				SELECT u.id, u.email, u.full_name, u.role,
				       coalesce(string_agg(b.code, ',') FILTER (WHERE b.active), '') AS branches
				FROM app_users u
				LEFT JOIN user_branches ub ON ub.user_id = u.id
				LEFT JOIN branches b ON b.id = ub.branch_id
				WHERE u.active
				GROUP BY u.id
				""")
			.query((rs, row) -> new Person(rs.getLong("id"), rs.getString("email"), rs.getString("full_name"),
					Role.valueOf(rs.getString("role")), Set.of(rs.getString("branches").split(","))))
			.list();
		CurrentUser jefferson = person(people, accounts, "Jefferson Rojas", Role.ADMIN, null, missing);
		CurrentUser susan = person(people, accounts, "Susan Rojas", Role.RECEPTIONIST, TRES_RIOS, missing);
		CurrentUser maicol = person(people, accounts, "Maicol Armas", Role.RECEPTIONIST, SAN_PEDRO, missing);
		CurrentUser julian = person(people, accounts, "Julián Alvarez", Role.TECHNICIAN, TRES_RIOS, missing);
		CurrentUser pedro = person(people, accounts, "Pedro Fernandez", Role.TECHNICIAN, SAN_PEDRO, missing);
		return new DemoActors(tresRios, sanPedro, jefferson, susan, maicol, julian, pedro, List.copyOf(missing));
	}

	boolean complete() {
		return missing.isEmpty();
	}

	private static long branch(JdbcClient jdbc, String code, List<String> missing) {
		Optional<Long> id = jdbc.sql("SELECT id FROM branches WHERE code = ? AND active").param(code)
			.query(Long.class)
			.optional();
		if (id.isEmpty()) {
			missing.add("sucursal activa " + code);
		}
		return id.orElse(-1L);
	}

	/**
	 * The one active account with that name (accents and case ignored) and role, assigned to the
	 * branch when one is required. The principal is loaded like a login does; its password hash is
	 * erased at once because nothing here authenticates.
	 */
	private static CurrentUser person(List<Person> people, UserDetailsService accounts, String fullName, Role role,
			String branchCode, List<String> missing) {
		List<Person> matches = people.stream()
			.filter(p -> p.role() == role && normalize(p.fullName()).equals(normalize(fullName)))
			.filter(p -> branchCode == null || p.branches().contains(branchCode))
			.toList();
		if (matches.size() != 1) {
			missing.add(fullName + " (" + role + (branchCode == null ? "" : ", " + branchCode) + ")"
					+ (matches.isEmpty() ? "" : ": nombre repetido"));
			return null;
		}
		CurrentUser user = (CurrentUser) accounts.loadUserByUsername(matches.get(0).email());
		user.eraseCredentials();
		return user;
	}

	private static String normalize(String name) {
		return Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ")
			.strip();
	}

}
