package dev.jeffrojas.electronicarojas.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.notifications.NotificationComposer.ComposedMessage;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;

/** C.2 templates: clear Spanish, only what the customer needs, dates in Costa Rica time. */
class NotificationComposerTest {

	private static final Map<String, Object> REPAIR = Map.of("orderCode", "OR-2026-000042", "deviceType", "Lavadora",
			"brand", "Whirlpool", "branchName", "San José Centro", "branchAddress", "Avenida 2, San José");

	/** 2026-10-05 15:00Z = Monday 09:00 in Costa Rica (UTC-6, no daylight saving). */
	private static final Map<String, Object> VISIT = Map.of("requestCode", "SR-2026-000007", "deviceType",
			"Refrigeradora", "brand", "LG", "branchName", "San José Centro", "publicRef",
			"ef582f46-4fd5-49ca-9903-b4b51f7e312d", "start", "2026-10-05T15:00:00Z", "end", "2026-10-05T16:30:00Z",
			"previousStart", "2026-10-02T19:00:00Z", "previousEnd", "2026-10-02T20:30:00Z");

	@Test
	void readyForPickupNamesTheOrderTheApplianceAndWhereToCollectIt() {
		ComposedMessage message = NotificationComposer.compose(EventType.REPAIR_READY_FOR_PICKUP, REPAIR,
				"Lucía Ficticia Mora", null);

		assertThat(message.subject()).isEqualTo("Tu equipo está listo para retirar · Orden OR-2026-000042");
		assertThat(message.body()).startsWith("Hola Lucía:")
			.contains("Tu equipo (lavadora Whirlpool, orden OR-2026-000042) está listo para retirar en San José Centro.")
			.contains("Dirección: Avenida 2, San José.")
			.contains("Recibes este aviso porque aceptaste avisos por correo")
			.doesNotContain("Ficticia", "Mora");
	}

	@Test
	void visitMessagesShowCostaRicaTimesAndTheStatusLinkOnlyWhenConfigured() {
		ComposedMessage confirmed = NotificationComposer.compose(EventType.VISIT_CONFIRMED, VISIT, "Lucía", null);
		assertThat(confirmed.subject()).isEqualTo("Visita confirmada · Solicitud SR-2026-000007");
		assertThat(confirmed.body()).contains("revisar tu equipo (refrigeradora LG):")
			.contains("lunes 5 de octubre de 2026, de 09:00 a 10:30").doesNotContain("http");

		ComposedMessage moved = NotificationComposer.compose(EventType.VISIT_RESCHEDULED, VISIT, "Lucía",
				"https://electronica-rojas.example");
		assertThat(moved.body()).contains("Antes: viernes 2 de octubre de 2026, de 13:00 a 14:30.")
			.contains("Ahora: lunes 5 de octubre de 2026, de 09:00 a 10:30.")
			.contains("https://electronica-rojas.example/solicitud/ef582f46-4fd5-49ca-9903-b4b51f7e312d");

		ComposedMessage cancelled = NotificationComposer.compose(EventType.VISIT_CANCELLED, VISIT, null, null);
		assertThat(cancelled.body()).startsWith("Hola :")
			.contains("fue cancelada")
			.contains("comunícate con San José Centro");
	}

	@Test
	void unknownParametersNeverLeakIntoTheText() {
		Map<String, Object> withInternals = new java.util.HashMap<>(REPAIR);
		withInternals.put("diagnosis", "Tarjeta madre dañada por humedad");
		withInternals.put("internalNotes", "Cliente difícil");
		ComposedMessage message = NotificationComposer.compose(EventType.REPAIR_READY_FOR_PICKUP, withInternals, "Ana",
				null);
		assertThat(message.subject() + message.body()).doesNotContain("humedad", "difícil");
	}

	@Test
	void workerHelpersMaskAddressesAndBackOff() {
		assertThat(NotificationDispatcher.mask("lucia@ejemplo.test")).isEqualTo("l***@ejemplo.test");
		assertThat(NotificationDispatcher.mask("x")).isEqualTo("***");
		assertThat(NotificationDispatcher.mask(null)).isNull();
		assertThat(NotificationDispatcher.backoff(1)).isEqualTo(Duration.ofMinutes(1));
		assertThat(NotificationDispatcher.backoff(2)).isEqualTo(Duration.ofMinutes(5));
		assertThat(NotificationDispatcher.backoff(3)).isEqualTo(Duration.ofMinutes(15));
		assertThat(NotificationDispatcher.backoff(9)).isEqualTo(Duration.ofMinutes(60));
	}

}
