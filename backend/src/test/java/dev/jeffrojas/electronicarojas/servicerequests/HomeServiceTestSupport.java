package dev.jeffrojas.electronicarojas.servicerequests;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;
import com.jayway.jsonpath.JsonPath;

/** Fixtures of the home-service tests. All people, phones and addresses are fictitious. */
abstract class HomeServiceTestSupport extends IntegrationTestSupport {

	/** A Monday at least a week ahead in Costa Rica, so every slot on it is in the future. */
	static LocalDate nextMonday() {
		return LocalDate.now(ClockConfig.BUSINESS_ZONE).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}

	/** "2030-03-04T09:00-06:00" style instant for a local time on a date. */
	static String at(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time)).atZone(ClockConfig.BUSINESS_ZONE).toInstant().toString();
	}

	static Instant instant(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time)).atZone(ClockConfig.BUSINESS_ZONE).toInstant();
	}

	/** Mon-Fri 08:00-17:00 with a 12:00-13:00 break at {@code branchId}. */
	protected void weekdayShifts(long technicianId, long branchId) {
		for (int day = 1; day <= 5; day++) {
			jdbc.update("""
					INSERT INTO technician_shifts (technician_id, branch_id, day_of_week, start_time, end_time,
					                               break_start, break_end, updated_by, updated_at)
					VALUES (?, ?, ?, '08:00', '17:00', '12:00', '13:00', ?, now())
					""", technicianId, branchId, day, technicianId);
		}
	}

	protected static String publicForm(UUID submissionId, long branchId, String name, String phone) {
		return """
				{"submissionId":"%s","branchId":%d,"contactName":"%s","contactPhone":"%s","contactEmail":"cliente@ejemplo.test",
				 "province":"SAN_JOSE","canton":"Montes de Oca","district":"San Pedro","addressLine":"Del parque 200 m al este, casa azul",
				 "deviceType":"Refrigeradora","brand":"LG","problemDescription":"No enfría la parte de abajo",
				 "preferredWindow":"MORNING","contactConsent":true,"notificationsConsent":true}
				""".formatted(submissionId, branchId, name, phone);
	}

	/** A request registered by staff with a new customer: already UNDER_REVIEW and linked. */
	protected long staffRequest(MockHttpSession session, long branchId, String name, String phone) throws Exception {
		String body = """
				{"submissionId":"%s","branchId":%d,"registerCustomer":true,"confirmedNewPerson":true,
				 "contactName":"%s","contactPhone":"%s","province":"HEREDIA","canton":"Heredia",
				 "addressLine":"Barrio Fátima, casa 12","deviceType":"Lavadora","brand":"Whirlpool",
				 "problemDescription":"No centrifuga","preferredWindow":"ANY"}
				""".formatted(UUID.randomUUID(), branchId, name, phone);
		String response = mockMvc.perform(post("/api/v1/service-requests").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	protected ResultActions schedule(MockHttpSession session, long requestId, long technicianId, String start,
			boolean confirm) throws Exception {
		return schedule(session, requestId, technicianId, start, confirm, UUID.randomUUID());
	}

	protected ResultActions schedule(MockHttpSession session, long requestId, long technicianId, String start,
			boolean confirm, UUID operationId) throws Exception {
		return mockMvc.perform(post("/api/v1/service-requests/{id}/visits", requestId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","technicianId":%d,"start":"%s","durationMinutes":90,"confirm":%s}
					""".formatted(operationId, technicianId, start, confirm)));
	}

	protected static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
