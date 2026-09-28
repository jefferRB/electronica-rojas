package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.security.Role;

/**
 * BR-SRV-003..008 against PostgreSQL: working hours, conflicts with confirmed visits (and not
 * with proposed ones), rescheduling with history, cancellation releasing the slot, idempotency,
 * the technician's flow and the link to a workshop order. Concurrency is in
 * VisitConcurrencyIntegrationTests.
 */
class VisitSchedulingIntegrationTests extends HomeServiceTestSupport {

	private long sanJose;

	private long technicianId;

	private long otherTechnicianId;

	private MockHttpSession manager;

	private MockHttpSession receptionist;

	private MockHttpSession technician;

	private LocalDate monday;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		otherTechnicianId = createUser("tecnico2@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		weekdayShifts(technicianId, sanJose);
		weekdayShifts(otherTechnicianId, sanJose);
		manager = login("gerente@electronica-rojas.test");
		receptionist = login("recepcion@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
		monday = nextMonday();
	}

	private ResultActions visitAction(MockHttpSession session, long visitId, String action, String json) throws Exception {
		var request = post("/api/v1/service-visits/{id}/" + action, visitId).session(session).with(xsrf());
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(request);
	}

	private ResultActions reschedule(long visitId, long techId, String start) throws Exception {
		return mockMvc.perform(put("/api/v1/service-visits/{id}/schedule", visitId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"technicianId":%d,"start":"%s","durationMinutes":90,"reason":"El cliente pidió otra hora"}
					""".formatted(techId, start)));
	}

	@Test
	void confirmingAVisitAcceptsTheRequestAndBlocksTheTechnician() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");

		schedule(receptionist, requestId, technicianId, at(monday, "08:00"), true).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.end").value(at(monday, "09:30")))
			.andExpect(jsonPath("$.blockedUntil").value(at(monday, "10:00")))
			.andExpect(jsonPath("$.technician.id").value(technicianId));
		mockMvc.perform(get("/api/v1/service-requests/{id}", requestId).session(receptionist))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.visits[0].status").value("CONFIRMED"))
			.andExpect(jsonPath("$.history[*].type").value(contains("SUBMITTED", "REVIEW_STARTED", "CUSTOMER_LINKED",
					"VISIT_CONFIRMED")));

		long second = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");
		// 09:30 is inside the 30-minute margin after the first visit.
		schedule(receptionist, second, technicianId, at(monday, "09:30"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
			.andExpect(jsonPath("$.conflictStart").value(at(monday, "08:00")));
		schedule(receptionist, second, technicianId, at(monday, "10:00"), true).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM service_visits WHERE status = 'CONFIRMED'", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void visitsOutsideWorkingHoursAreRejected() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");

		schedule(receptionist, requestId, technicianId, at(monday, "07:00"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"))
			.andExpect(jsonPath("$.shiftStart").value("08:00"));
		schedule(receptionist, requestId, technicianId, at(monday, "16:00"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
		schedule(receptionist, requestId, technicianId, at(monday, "11:30"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DURING_BREAK"));
		schedule(receptionist, requestId, technicianId, at(monday.plusDays(5), "09:00"), true)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_SHIFT"));
		schedule(receptionist, requestId, technicianId, "2020-01-06T15:00:00Z", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("VISIT_IN_PAST"));
		long foreignTechnician = createUser("tecnico.al@electronica-rojas.test", Role.TECHNICIAN, createBranch("AL-01"));
		schedule(receptionist, requestId, foreignTechnician, at(monday, "09:00"), true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("TECHNICIAN_NOT_ELIGIBLE"));
		assertThat(countRows("service_visits")).isZero();
	}

	@Test
	void aProposedVisitDoesNotBlockButCannotBeConfirmedOverAConfirmedOne() throws Exception {
		long proposed = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, proposed, technicianId, at(monday, "09:00"), false)
			.andExpect(jsonPath("$.status").value("PROPOSED")));
		mockMvc.perform(get("/api/v1/service-requests/{id}", proposed).session(receptionist))
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

		long confirmed = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");
		schedule(receptionist, confirmed, technicianId, at(monday, "09:30"), true).andExpect(status().isCreated());

		visitAction(receptionist, visitId, "confirm", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
		reschedule(visitId, technicianId, at(monday, "13:00")).andExpect(status().isOk());
		visitAction(receptionist, visitId, "confirm", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		// Confirming twice is harmless.
		visitAction(receptionist, visitId, "confirm", null).andExpect(status().isOk());
	}

	@Test
	void reschedulingKeepsTheHistoryAndReleasesTheOldSlot() throws Exception {
		long first = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, first, technicianId, at(monday, "09:00"), true));

		reschedule(visitId, otherTechnicianId, at(monday, "14:00")).andExpect(status().isOk())
			.andExpect(jsonPath("$.technician.id").value(otherTechnicianId))
			.andExpect(jsonPath("$.start").value(at(monday, "14:00")));
		assertThat(jdbc.queryForMap("""
				SELECT details->>'previousStart' AS previous, details->>'previousTechnicianName' AS tech, reason
				FROM service_request_events WHERE event_type = 'VISIT_RESCHEDULED'"""))
			.containsEntry("previous", at(monday, "09:00"))
			.containsEntry("tech", "Usuario TECHNICIAN")
			.containsEntry("reason", "El cliente pidió otra hora");
		assertThat(countRows("service_visits")).isEqualTo(1);

		long second = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");
		schedule(receptionist, second, technicianId, at(monday, "09:00"), true).andExpect(status().isCreated());
	}

	@Test
	void cancellingAVisitFreesTheSlotAndSendsTheRequestBackToReview() throws Exception {
		long first = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, first, technicianId, at(monday, "09:00"), true));

		visitAction(receptionist, visitId, "cancel", "{\"reason\":\"El cliente no estará\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		mockMvc.perform(get("/api/v1/service-requests/{id}", first).session(receptionist))
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.actions.canSchedule").value(true));
		mockMvc.perform(get("/api/v1/service-visits/availability").param("technicianId", String.valueOf(technicianId))
			.param("date", monday.toString()).param("durationMinutes", "90").session(receptionist))
			.andExpect(jsonPath("$.freeStarts").value(hasItem("09:00:00")))
			.andExpect(jsonPath("$.confirmed").isEmpty());

		long second = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");
		schedule(receptionist, second, technicianId, at(monday, "09:00"), true).andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/service-visits/availability").param("technicianId", String.valueOf(technicianId))
			.param("date", monday.toString()).param("durationMinutes", "90").session(receptionist))
			.andExpect(jsonPath("$.freeStarts").value(not(hasItem("09:00:00"))))
			.andExpect(jsonPath("$.freeStarts").value(hasItem("13:00:00")));
	}

	@Test
	void aDoubleClickSchedulesOneVisit() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		UUID operation = UUID.randomUUID();
		long first = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true, operation));
		long second = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true, operation)
			.andExpect(status().isCreated()));
		assertThat(second).isEqualTo(first);
		assertThat(countRows("service_visits")).isEqualTo(1);
		schedule(receptionist, requestId, technicianId, at(monday, "14:00"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VISIT_ALREADY_ACTIVE"));
	}

	@Test
	void aVisitNeedsALinkedCustomer() throws Exception {
		mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(publicForm(UUID.randomUUID(), sanJose, "Ana Pérez", "8888-7777"))).andExpect(status().isAccepted());
		long requestId = jdbc.queryForObject("SELECT id FROM service_requests", Long.class);
		schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CUSTOMER_NOT_LINKED"));
	}

	@Test
	void techniciansSeeOnlyTheirVisitsAndTheContactOnlyWhileTheVisitIsAhead() throws Exception {
		long mine = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, mine, technicianId, at(monday, "09:00"), true));
		long theirs = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");
		long otherVisit = idOf(schedule(receptionist, theirs, otherTechnicianId, at(monday, "09:00"), true));

		mockMvc.perform(get("/api/v1/service-visits").param("from", at(monday, "00:00")).param("to", at(monday.plusDays(1), "00:00"))
			.session(technician))
			.andExpect(jsonPath("$[*].id").value(contains((int) visitId)))
			.andExpect(jsonPath("$[0].contactPhone").value("+50688887777"))
			.andExpect(jsonPath("$[0].addressLine").value("Barrio Fátima, casa 12"))
			.andExpect(jsonPath("$[0].actions.canStart").value(true))
			.andExpect(jsonPath("$[0].actions.canReschedule").value(false));
		mockMvc.perform(get("/api/v1/service-visits/{id}", otherVisit).session(technician)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/service-requests/{id}", mine).session(technician)).andExpect(status().isForbidden());
		// Starting a visit of a later day is refused.
		visitAction(technician, visitId, "start", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VISIT_NOT_TODAY"));
		visitAction(technician, otherVisit, "start", null).andExpect(status().isNotFound());
	}

	@Test
	void aVisitThatNeedsTheWorkshopOpensAnOrderForTheSameCustomer() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true));
		// Test fixture: move the confirmed visit to earlier today so it can be started now.
		jdbc.update("""
				UPDATE service_visits SET scheduled_start = now() - INTERVAL '1 minute',
				       scheduled_end = now() + INTERVAL '89 minutes', blocked_until = now() + INTERVAL '119 minutes'
				WHERE id = ?""", visitId);

		visitAction(technician, visitId, "start", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		visitAction(receptionist, visitId, "cancel", "{\"reason\":\"x\"}").andExpect(status().isConflict());
		visitAction(technician, visitId, "complete", "{\"outcome\":\"NEEDS_WORKSHOP\",\"notes\":\"Compresor dañado\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.contactPhone").doesNotExist())
			.andExpect(jsonPath("$.actions.canLinkRepairOrder").value(false));
		visitAction(technician, visitId, "repair-order", "{}").andExpect(status().isForbidden());

		UUID operation = UUID.randomUUID();
		String link = """
				{"operationId":"%s","physicalCondition":"Rayones en la puerta","accessories":"Ninguno"}
				""".formatted(operation);
		visitAction(receptionist, visitId, "repair-order", link).andExpect(status().isOk())
			.andExpect(jsonPath("$.repairOrder.orderCode").value(org.hamcrest.Matchers.startsWith("OR-")));
		assertThat(jdbc.queryForMap("""
				SELECT o.customer_id = r.customer_id AS same_customer, o.branch_id = r.branch_id AS same_branch,
				       o.reported_fault
				FROM service_visits v JOIN service_requests r ON r.id = v.request_id JOIN repair_orders o ON o.id = v.repair_order_id"""))
			.containsEntry("same_customer", true)
			.containsEntry("same_branch", true)
			.satisfies(row -> assertThat((String) row.get("reported_fault")).contains("No centrifuga", "Compresor dañado"));
		assertThat(countRows("customers")).isEqualTo(1);
		mockMvc.perform(get("/api/v1/service-visits/by-repair-order/{id}",
				jdbc.queryForObject("SELECT id FROM repair_orders", Long.class)).session(receptionist))
			.andExpect(jsonPath("$.id").value(visitId));
		// Linking again with another order is refused; the history has every step.
		visitAction(receptionist, visitId, "repair-order", "{\"existingOrderId\":999999}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VISIT_ALREADY_LINKED"));
		assertThat(jdbc.queryForList("SELECT event_type FROM service_request_events WHERE request_id = ? ORDER BY id",
				String.class, requestId))
			.containsSubsequence("VISIT_CONFIRMED", "VISIT_STARTED", "VISIT_COMPLETED", "REPAIR_ORDER_LINKED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'SERVICE_VISIT_UPDATED'", Integer.class))
			.isEqualTo(4);
	}

	@Test
	void theDatabaseItselfRefusesOverlappingConfirmedVisits() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		long visitId = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true));
		long other = staffRequest(receptionist, sanJose, "Luis Mora", "7000-1234");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO service_visits (request_id, operation_id, technician_id, status, scheduled_start, scheduled_end,
				                            blocked_until, created_by, created_at, updated_at)
				SELECT ?, gen_random_uuid(), technician_id, 'CONFIRMED', scheduled_start + INTERVAL '30 minutes',
				       scheduled_end + INTERVAL '30 minutes', blocked_until + INTERVAL '30 minutes', created_by, now(), now()
				FROM service_visits WHERE id = ?""", other, visitId)).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM service_visits WHERE id = ?", visitId))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE service_request_events SET reason = 'x'"))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void aManagerKeepsTheDaysATechnicianWorksAtAnotherBranch() throws Exception {
		long alajuela = createBranch("AL-01");
		long shared = createUser("tecnico.compartido@electronica-rojas.test", Role.TECHNICIAN, sanJose, alajuela);
		jdbc.update("""
				INSERT INTO technician_shifts (technician_id, branch_id, day_of_week, start_time, end_time, updated_by, updated_at)
				VALUES (?, ?, 6, '08:00', '12:00', ?, now())""", shared, alajuela, shared);

		mockMvc.perform(put("/api/v1/technician-schedules/{id}", shared).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("""
					{"shifts":[{"dayOfWeek":1,"branchId":%d,"start":"08:00","end":"17:00"}]}""".formatted(sanJose)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shifts[*].dayOfWeek").value(contains(1, 6)));
		mockMvc.perform(put("/api/v1/technician-schedules/{id}", shared).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("""
					{"shifts":[{"dayOfWeek":6,"branchId":%d,"start":"08:00","end":"17:00"}]}""".formatted(sanJose)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("DAY_AT_OTHER_BRANCH"));
		mockMvc.perform(put("/api/v1/technician-schedules/{id}", shared).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("""
					{"shifts":[{"dayOfWeek":2,"branchId":%d,"start":"08:00","end":"17:00"}]}""".formatted(alajuela)))
			.andExpect(status().isForbidden());
	}

	@Test
	void workingHoursAreConfiguredByManagementOnly() throws Exception {
		String week = """
				{"shifts":[{"dayOfWeek":1,"branchId":%d,"start":"09:00","end":"13:00"},
				           {"dayOfWeek":3,"branchId":%d,"start":"13:00","end":"18:00","breakStart":"15:00","breakEnd":"15:30"}]}
				""".formatted(sanJose, sanJose);
		mockMvc.perform(put("/api/v1/technician-schedules/{id}", technicianId).session(receptionist)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(week)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/technician-schedules/{id}", technicianId).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(week))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shifts[*].dayOfWeek").value(contains(1, 3)));
		mockMvc.perform(put("/api/v1/technician-schedules/{id}", technicianId).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("""
					{"shifts":[{"dayOfWeek":2,"branchId":%d,"start":"12:00","end":"09:00"}]}""".formatted(sanJose)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("SHIFT_INVALID"));

		long requestId = staffRequest(receptionist, sanJose, "Ana Pérez", "8888-7777");
		schedule(receptionist, requestId, technicianId, at(monday, "13:30"), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));

		mockMvc.perform(put("/api/v1/branches/{id}/service-settings", sanJose).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"defaultVisitMinutes\":60,\"bufferMinutes\":15}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/service-requests/{id}/visits", requestId).session(receptionist)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("""
					{"operationId":"%s","technicianId":%d,"start":"%s","confirm":true}""".formatted(UUID.randomUUID(),
					technicianId, at(monday, "09:00"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.end").value(at(monday, "10:00")))
			.andExpect(jsonPath("$.blockedUntil").value(at(monday, "10:15")));
	}

}
