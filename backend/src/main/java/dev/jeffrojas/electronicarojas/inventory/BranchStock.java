package dev.jeffrojas.electronicarojas.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;

/**
 * Units of one product at one branch (BR-INV-002). Only {@link StockLedger} changes the quantity,
 * always after taking a row lock, and always together with a {@link StockMovement}.
 */
@Entity
@Table(name = "branch_stock")
public class BranchStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false, updatable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, updatable = false)
	private Product product;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false)
	private int minimumQuantity;

	@Column(nullable = false)
	private Instant updatedAt;

	/**
	 * Optimistic version. Quantity changes are already serialized by the pessimistic row lock;
	 * the version still protects any write path that forgets to lock.
	 */
	@Version
	private long version;

	protected BranchStock() {
	}

	/** Rows are normally created by an upsert in the repository; this constructor serves tests. */
	BranchStock(Branch branch, Product product, int quantity, int minimumQuantity, Instant now) {
		this.branch = branch;
		this.product = product;
		this.quantity = quantity;
		this.minimumQuantity = minimumQuantity;
		this.updatedAt = now;
	}

	/**
	 * Applies a signed change and returns the new balance. Never lets stock go negative
	 * (BR-TRF-002); the database CHECK is the last line of defense.
	 */
	int apply(int delta, Instant now) {
		if (delta == 0) {
			throw new IllegalArgumentException("A stock change cannot be zero");
		}
		long result = (long) quantity + delta;
		if (result < 0) {
			throw new InsufficientStockException(branchId(), quantity, -delta);
		}
		if (result > Integer.MAX_VALUE) {
			throw new ConflictException("QUANTITY_TOO_LARGE", "The resulting quantity exceeds the supported maximum.");
		}
		quantity = (int) result;
		updatedAt = now;
		return quantity;
	}

	void changeMinimum(int minimumQuantity, Instant now) {
		if (minimumQuantity < 0) {
			throw new IllegalArgumentException("Minimum cannot be negative");
		}
		this.minimumQuantity = minimumQuantity;
		this.updatedAt = now;
	}

	/** BR-INV-005: derived from the current values, never stored as a separate counter. */
	StockStatus status() {
		return StockStatus.of(quantity, minimumQuantity);
	}

	private long branchId() {
		return branch == null || branch.getId() == null ? 0 : branch.getId();
	}

	public Long getId() {
		return id;
	}

	public Branch getBranch() {
		return branch;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getMinimumQuantity() {
		return minimumQuantity;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
