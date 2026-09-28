package dev.jeffrojas.electronicarojas.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Article of the single company-wide catalog (BR-INV-001). Stock does NOT live here: it belongs
 * to each branch ({@link BranchStock}). Products are deactivated, never deleted (BR-INV-004).
 */
@Entity
@Table(name = "products")
public class Product {

	static final String UNIT = "UNIT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Normalized business key, immutable after creation. */
	@Column(nullable = false, length = 40, updatable = false)
	private String sku;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(nullable = false, length = 80)
	private String category;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductKind kind;

	@Column(nullable = false, length = 10, updatable = false)
	private String unit;

	@Column(nullable = false)
	private boolean active;

	/** What the company paid for one unit (internal; see {@link PricingPolicy}). Null = unknown. */
	@Column(precision = 12, scale = 2)
	private BigDecimal unitCost;

	/** Suggested price per unit charged to customers. Null = not defined yet. */
	@Column(precision = 12, scale = 2)
	private BigDecimal salePrice;

	/** Default of a repair part line; each line may decide otherwise (BR-REP-014). */
	@Column(nullable = false)
	private boolean chargeableByDefault;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Product() {
	}

	Product(String sku, String name, String category, String description, ProductKind kind, Pricing pricing,
			Instant now) {
		this.sku = normalizeSku(sku);
		this.unit = UNIT;
		this.active = true;
		this.createdAt = now;
		apply(name, category, description, kind, pricing, now);
	}

	/**
	 * Money of a product. Changing it affects only operations recorded afterwards: repair lines keep
	 * the values they were recorded with (BR-INV-007).
	 */
	record Pricing(BigDecimal unitCost, BigDecimal salePrice, boolean chargeableByDefault) {
	}

	/** Trimmed and upper-cased, so "ab-10 " and "AB-10" are the same SKU. */
	static String normalizeSku(String sku) {
		return sku.strip().toUpperCase(Locale.ROOT);
	}

	void update(String name, String category, String description, ProductKind kind, Pricing pricing, boolean active,
			Instant now) {
		apply(name, category, description, kind, pricing, now);
		this.active = active;
	}

	private void apply(String name, String category, String description, ProductKind kind, Pricing pricing,
			Instant now) {
		this.name = name.strip();
		this.category = category.strip();
		this.description = description == null || description.isBlank() ? null : description.strip();
		this.kind = kind;
		this.unitCost = pricing.unitCost();
		this.salePrice = pricing.salePrice();
		this.chargeableByDefault = pricing.chargeableByDefault();
		this.updatedAt = now;
	}

	Pricing pricing() {
		return new Pricing(unitCost, salePrice, chargeableByDefault);
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public ProductKind getKind() {
		return kind;
	}

	public String getUnit() {
		return unit;
	}

	public boolean isActive() {
		return active;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public BigDecimal getSalePrice() {
		return salePrice;
	}

	public boolean isChargeableByDefault() {
		return chargeableByDefault;
	}

	public long getVersion() {
		return version;
	}

}
