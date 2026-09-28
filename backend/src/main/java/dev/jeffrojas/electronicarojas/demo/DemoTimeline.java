package dev.jeffrojas.electronicarojas.demo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.jeffrojas.electronicarojas.security.CurrentUser;

/**
 * The scenario as a list of operations, each done by one collaborator at one instant. They run in
 * chronological order; for each one the clock is moved to its instant and the security context
 * holds that collaborator, exactly what a logged-in request carries, so the use case applies its
 * role and branch checks. The persistence context is flushed and cleared after every operation,
 * like the end of a request, so each use case reads fresh state from the database.
 * <p>
 * Operations whose instant has not arrived yet (later today, future visits) are left out: the
 * records simply stay in the state they have now.
 */
final class DemoTimeline {

	private record Step(Instant at, int sequence, CurrentUser actor, String label, Runnable action) {
	}

	record Outcome(int executed, int pending) {
	}

	/** A failed operation: names it, so a broken scenario is easy to fix. */
	static final class StepFailedException extends RuntimeException {

		StepFailedException(String label, Instant at, RuntimeException cause) {
			super("Operation '" + label + "' at " + at + " failed: " + cause.getMessage(), cause);
		}

	}

	private final List<Step> steps = new ArrayList<>();

	/** @param actor null for the anonymous public form */
	void add(Instant at, CurrentUser actor, String label, Runnable action) {
		steps.add(new Step(at, steps.size(), actor, label, action));
	}

	int size() {
		return steps.size();
	}

	Outcome run(Instant notAfter, DemoClock clock, EntityManager entityManager) {
		List<Step> ordered = steps.stream()
			.sorted(Comparator.comparing(Step::at).thenComparingInt(Step::sequence))
			.toList();
		int executed = 0;
		for (Step step : ordered) {
			if (step.at().isAfter(notAfter)) {
				continue;
			}
			clock.travelTo(step.at());
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			if (step.actor() != null) {
				context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(step.actor(), null,
						step.actor().getAuthorities()));
			}
			SecurityContextHolder.setContext(context);
			try {
				step.action().run();
				entityManager.flush();
				entityManager.clear();
			}
			catch (RuntimeException ex) {
				throw new StepFailedException(step.label(), step.at(), ex);
			}
			finally {
				SecurityContextHolder.clearContext();
			}
			executed++;
		}
		return new Outcome(executed, steps.size() - executed);
	}

}
