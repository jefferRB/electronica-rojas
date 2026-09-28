package dev.jeffrojas.electronicarojas.inventory;

/** Product status filter for listings; inactive products stay queryable (BR-INV-004). */
enum StatusFilter {

	ACTIVE(Boolean.TRUE),
	INACTIVE(Boolean.FALSE),
	ALL(null);

	private final Boolean active;

	StatusFilter(Boolean active) {
		this.active = active;
	}

	Boolean active() {
		return active;
	}

}
