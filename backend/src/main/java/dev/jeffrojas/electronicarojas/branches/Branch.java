package dev.jeffrojas.electronicarojas.branches;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Physical branch (ER-BR-001 section 2). Any number of branches can exist. A deactivated branch
 * keeps its history and cannot receive new operations (BR-BRH-001); it is never deleted.
 */
@Entity
@Table(name = "branches")
public class Branch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Business key: normalized to upper case and immutable once created. */
	@Column(nullable = false, length = 20, updatable = false)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 300)
	private String address;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	/** Required by JPA. */
	protected Branch() {
	}

	public Branch(String code, String name, String address, Instant now) {
		this.code = normalizeCode(code);
		this.name = name.strip();
		this.address = blankToNull(address);
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static String normalizeCode(String code) {
		return code.strip().toUpperCase(Locale.ROOT);
	}

	void update(String name, String address, boolean active, Instant now) {
		this.name = name.strip();
		this.address = blankToNull(address);
		this.active = active;
		this.updatedAt = now;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public boolean isActive() {
		return active;
	}

	public long getVersion() {
		return version;
	}

	/** Equality by the immutable business key, safe inside hash-based collections. */
	@Override
	public boolean equals(Object other) {
		return other instanceof Branch that && code != null && code.equals(that.code);
	}

	@Override
	public int hashCode() {
		return code == null ? 0 : code.hashCode();
	}

}
