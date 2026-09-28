package dev.jeffrojas.electronicarojas.audit;

/** Auditable business actions and the entity type each one refers to. */
public enum AuditAction {

	BRANCH_CREATED("BRANCH"),
	BRANCH_UPDATED("BRANCH"),
	USER_CREATED("USER"),
	USER_UPDATED("USER"),
	USER_PASSWORD_RESET("USER"),
	PRODUCT_CREATED("PRODUCT"),
	PRODUCT_UPDATED("PRODUCT"),
	/** Sale price, unit cost or default chargeability changed; details carry before and after. */
	PRODUCT_PRICING_CHANGED("PRODUCT"),
	STOCK_MINIMUM_CHANGED("BRANCH_STOCK"),
	STOCK_MOVEMENT_RECORDED("STOCK_MOVEMENT"),
	STOCK_TRANSFER_COMPLETED("STOCK_TRANSFER"),
	CUSTOMER_CREATED("CUSTOMER"),
	CUSTOMER_UPDATED("CUSTOMER"),
	/** A notification consent granted or withdrawn; details carry channel, source and text version. */
	CUSTOMER_CONSENT_RECORDED("CUSTOMER_CONSENT"),
	REPAIR_ORDER_RECEIVED("REPAIR_ORDER"),
	REPAIR_STATUS_CHANGED("REPAIR_ORDER"),
	REPAIR_TECHNICIAN_ASSIGNED("REPAIR_ORDER"),
	REPAIR_DIAGNOSIS_UPDATED("REPAIR_ORDER"),
	REPAIR_QUOTE_CREATED("REPAIR_QUOTE"),
	REPAIR_QUOTE_DECIDED("REPAIR_QUOTE"),
	REPAIR_PART_CONSUMED("REPAIR_PART"),
	/** Correction of a consumed part; details.orderClosed flags corrections after the work ended. */
	REPAIR_PART_RETURNED("REPAIR_PART_RETURN"),
	SERVICE_REQUEST_SUBMITTED("SERVICE_REQUEST"),
	/** Any later decision on a request; details.event says which (ServiceEnums.EventType). */
	SERVICE_REQUEST_UPDATED("SERVICE_REQUEST"),
	/** Any change of a visit; details.event says which. */
	SERVICE_VISIT_UPDATED("SERVICE_VISIT"),
	TECHNICIAN_SCHEDULE_UPDATED("TECHNICIAN_SCHEDULE"),
	SERVICE_SETTINGS_UPDATED("SERVICE_SETTINGS"),
	/** Public portal rules, messages or on/off switch; details.enabledChanged flags the switch. */
	PUBLIC_PORTAL_UPDATED("PUBLIC_PORTAL"),
	/** New public address; the previous slug keeps resolving to the new one. */
	PUBLIC_PORTAL_SLUG_CHANGED("PUBLIC_PORTAL"),
	NOTIFICATION_RETRY_REQUESTED("NOTIFICATION");

	private final String entityType;

	AuditAction(String entityType) {
		this.entityType = entityType;
	}

	public String entityType() {
		return entityType;
	}

}
