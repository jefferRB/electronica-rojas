package dev.jeffrojas.electronicarojas.repairs;

/** Workshop statuses (ER-BR-001 section 7, v1.2 adds UNREPAIRABLE). */
public enum RepairStatus {

	/** Ingresado. */
	RECEIVED,
	/** En diagnóstico. */
	DIAGNOSING,
	/** Pendiente de autorización del cliente (hay una cotización pendiente). */
	AWAITING_APPROVAL,
	/** Cotización aprobada; trabajo aún no iniciado. */
	APPROVED,
	/** En reparación. */
	IN_REPAIR,
	/** Reparado y listo para entregar (físicamente listo, no "notificado": BR-REP-006). */
	READY_FOR_PICKUP,
	/** Equipo entregado al cliente. Terminal: no se entrega dos veces. */
	DELIVERED,
	/** Trabajo cancelado (incluye cotización rechazada). El equipo sigue en custodia hasta DELIVERED. */
	CANCELLED,
	/** Diagnosticado como no reparable. El equipo sigue en custodia hasta DELIVERED. */
	UNREPAIRABLE

}
