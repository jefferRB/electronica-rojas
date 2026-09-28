package dev.jeffrojas.electronicarojas.repairs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
import dev.jeffrojas.electronicarojas.inventory.Product;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * One spare part consumed in a repair order (BR-REP-007/011). Created together with its
 * OUT_FOR_REPAIR stock movement. Everything but {@code returnedQuantity} is history and never
 * changes (a V10/V12 trigger enforces it); corrections are {@link RepairPartReturn} rows.
 * <p>
 * The price, cost and chargeability are a snapshot taken when the part was used (BR-REP-014):
 * changing the catalog later never changes what this line says.
 */
@Entity
@Table(name = "repair_part_usages")
public class RepairPartUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private RepairOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, updatable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false, updatable = false)
	private Branch branch;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(nullable = false)
	private int returnedQuantity;

	@Column(nullable = false, updatable = false)
	private UUID operationId;

	@Column(nullable = false, length = 64, updatable = false)
	private String requestFingerprint;

	@Column(nullable = false, updatable = false)
	private long movementId;

	@Column(length = 300, updatable = false)
	private String note;

	/** Price per unit for this repair (catalog price then, or a management override). */
	@Column(precision = 12, scale = 2, updatable = false)
	private BigDecimal unitPrice;

	/** Internal cost per unit then; shown only to roles allowed to see costs. */
	@Column(precision = 12, scale = 2, updatable = false)
	private BigDecimal unitCost;

	@Column(nullable = false, updatable = false)
	private boolean chargeable;

	@Column(nullable = false, updatable = false)
	private boolean priceOverridden;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recorded_by", nullable = false, updatable = false)
	private AppUser recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	@Version
	private long version;

	protected RepairPartUsage() {
	}

	RepairPartUsage(RepairOrder order, Product product, Branch branch, int quantity, PartCharges.LinePricing pricing,
			UUID operationId, String requestFingerprint, long movementId, String note, AppUser recordedBy,
			Instant now) {
		this.order = order;
		this.product = product;
		this.branch = branch;
		this.quantity = quantity;
		this.unitPrice = pricing.unitPrice();
		this.unitCost = product.getUnitCost();
		this.chargeable = pricing.chargeable();
		this.priceOverridden = pricing.priceOverridden();
		this.operationId = operationId;
		this.requestFingerprint = requestFingerprint;
		this.movementId = movementId;
		this.note = note;
		this.recordedBy = recordedBy;
		this.recordedAt = now;
	}

	/** The line as the money calculations see it: units still in use and its snapshot. */
	PartCharges.Line charges() {
		return new PartCharges.Line(remainingQuantity(), unitPrice, unitCost, chargeable);
	}

	/** Units still counted as used in the repair. */
	int remainingQuantity() {
		return quantity - returnedQuantity;
	}

	/** Called under the usage's row lock after checking {@link #remainingQuantity()}. */
	void registerReturn(int units) {
		if (units <= 0 || units > remainingQuantity()) {
			throw new IllegalArgumentException("Return exceeds the remaining quantity");
		}
		returnedQuantity += units;
	}

	public Long getId() {
		return id;
	}

	public RepairOrder getOrder() {
		return order;
	}

	public Product getProduct() {
		return product;
	}

	public Branch getBranch() {
		return branch;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getReturnedQuantity() {
		return returnedQuantity;
	}

	public UUID getOperationId() {
		return operationId;
	}

	String getRequestFingerprint() {
		return requestFingerprint;
	}

	public long getMovementId() {
		return movementId;
	}

	public String getNote() {
		return note;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public boolean isChargeable() {
		return chargeable;
	}

	public boolean isPriceOverridden() {
		return priceOverridden;
	}

	public AppUser getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

}
