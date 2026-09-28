package dev.jeffrojas.electronicarojas.notifications;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;

/**
 * Builds the Spanish text of each notification from its template parameters (C.2). Pure: no Spring,
 * no database, no transport, so every template is unit-tested. Plain text only (no HTML to escape).
 * <p>
 * Only what the customer needs: their first name, the order or request code, the appliance, the
 * branch, and dates in Costa Rica time. Never diagnoses, internal notes, prices, technicians,
 * staff reasons or links to the administration.
 */
final class NotificationComposer {

	private static final Locale SPANISH = Locale.forLanguageTag("es-CR");

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

	private NotificationComposer() {
	}

	record ComposedMessage(String subject, String body) {
	}

	/**
	 * @param customerName current full name of the customer (only the first word is used)
	 * @param publicBaseUrl base of the public site, or null to leave the status link out
	 */
	static ComposedMessage compose(EventType type, Map<String, Object> params, String customerName,
			String publicBaseUrl) {
		String greeting = "Hola " + firstName(customerName) + ":\n\n";
		String branch = text(params, "branchName", "la sucursal");
		String signature = "\n\nElectrónica Rojas · " + branch
				+ "\nRecibes este aviso porque aceptaste avisos por correo. Si ya no quieres recibirlos, "
				+ "avísanos en la sucursal o por teléfono.";
		return switch (type) {
			case REPAIR_READY_FOR_PICKUP -> {
				String order = text(params, "orderCode", "");
				String address = text(params, "branchAddress", null);
				yield new ComposedMessage("Tu equipo está listo para retirar · Orden " + order,
						greeting + "Tu equipo (" + device(params) + ", orden " + order + ") está listo para retirar en " + branch + "."
								+ (address == null ? "" : "\nDirección: " + address + ".")
								+ "\n\nAl venir, menciona el código de la orden." + signature);
			}
			case VISIT_CONFIRMED -> new ComposedMessage("Visita confirmada · Solicitud " + text(params, "requestCode", ""),
					greeting + "Confirmamos la visita a domicilio para revisar tu equipo (" + device(params) + "):\n"
							+ slot(params, "start", "end") + "." + statusLink(params, publicBaseUrl) + signature);
			case VISIT_RESCHEDULED -> new ComposedMessage(
					"Tu visita cambió de horario · Solicitud " + text(params, "requestCode", ""),
					greeting + "La visita a domicilio para revisar tu equipo (" + device(params) + ") cambió de horario.\n"
							+ "Antes: " + slot(params, "previousStart", "previousEnd") + ".\n" + "Ahora: "
							+ slot(params, "start", "end") + "." + statusLink(params, publicBaseUrl) + signature);
			case VISIT_CANCELLED -> new ComposedMessage("Visita cancelada · Solicitud " + text(params, "requestCode", ""),
					greeting + "La visita a domicilio programada para " + slot(params, "start", "end")
							+ " fue cancelada.\nSi todavía necesitas el servicio, comunícate con " + branch
							+ " para coordinar una nueva fecha." + statusLink(params, publicBaseUrl) + signature);
		};
	}

	/** "lunes 28 de septiembre de 2026, de 09:00 a 10:30" in Costa Rica time. */
	static String slot(Map<String, Object> params, String startKey, String endKey) {
		String start = text(params, startKey, null);
		String end = text(params, endKey, null);
		if (start == null) {
			return "la fecha acordada";
		}
		ZonedDateTime from = Instant.parse(start).atZone(ClockConfig.BUSINESS_ZONE);
		String day = DAY.format(from);
		return day + ", de " + TIME.format(from)
				+ (end == null ? "" : " a " + TIME.format(Instant.parse(end).atZone(ClockConfig.BUSINESS_ZONE)));
	}

	private static String device(Map<String, Object> params) {
		String type = text(params, "deviceType", "equipo").toLowerCase(SPANISH);
		String brand = text(params, "brand", null);
		return brand == null ? type : type + " " + brand;
	}

	private static String statusLink(Map<String, Object> params, String publicBaseUrl) {
		String ref = text(params, "publicRef", null);
		return publicBaseUrl == null || ref == null ? ""
				: "\n\nPuedes consultar el estado de tu solicitud en: " + publicBaseUrl + "/solicitud/" + ref;
	}

	static String firstName(String fullName) {
		if (fullName == null || fullName.isBlank()) {
			return "";
		}
		return fullName.strip().split("\\s+")[0];
	}

	private static String text(Map<String, Object> params, String key, String fallback) {
		Object value = params.get(key);
		return value == null || value.toString().isBlank() ? fallback : value.toString();
	}

}
