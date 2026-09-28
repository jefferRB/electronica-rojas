package dev.jeffrojas.electronicarojas.demo;

import static dev.jeffrojas.electronicarojas.customers.ConsentSource.IN_PERSON;
import static dev.jeffrojas.electronicarojas.customers.ConsentSource.PHONE;
import static dev.jeffrojas.electronicarojas.customers.ConsentSource.WRITTEN;
import static dev.jeffrojas.electronicarojas.customers.ContactChannel.EMAIL;
import static dev.jeffrojas.electronicarojas.customers.ContactChannel.WHATSAPP;
import static dev.jeffrojas.electronicarojas.inventory.MovementType.ADJUSTMENT_IN;
import static dev.jeffrojas.electronicarojas.inventory.MovementType.ADJUSTMENT_OUT;
import static dev.jeffrojas.electronicarojas.inventory.MovementType.ISSUE;
import static dev.jeffrojas.electronicarojas.inventory.MovementType.RECEIPT;
import static dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision.APPROVED;
import static dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision.REJECTED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DELIVERED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DIAGNOSING;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.IN_REPAIR;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.READY_FOR_PICKUP;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.UNREPAIRABLE;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow.AFTERNOON;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow.MORNING;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome.NEEDS_WORKSHOP;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome.NOT_RESOLVED;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome.RESOLVED_ON_SITE;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;

import dev.jeffrojas.electronicarojas.customers.ConsentSource;
import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.ConsentGrant;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CreateCustomerRequest;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerResponse;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.NewCustomer;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.RecordConsentRequest;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.UpdateCustomerRequest;
import dev.jeffrojas.electronicarojas.customers.NotificationConsentText;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateProductRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateTransferRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.InitialStock;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.RecordMovementRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.UpdateProductRequest;
import dev.jeffrojas.electronicarojas.inventory.MovementType;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.AssignTechnicianRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ConsumePartRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateQuoteRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateRepairOrderRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.DiagnosisRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.QuoteDecisionRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ReturnPartRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.TransitionRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.DecisionMethod;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;
import dev.jeffrojas.electronicarojas.repairs.RepairStatus;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ChangeBranchRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.CompleteVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.DecisionRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkCustomerRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkRepairOrderRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicSubmission;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.RescheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ScheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestDetail;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ShiftInput;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.StaffRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.WeekShiftsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceRequestService.RequestFilter;

/**
 * DEV-ONLY. The story of the demo data: about nine weeks of work at the two branches, told as
 * the operations the staff would have performed, each one through the same public use case the
 * REST API calls (validation, role and branch checks, locks, idempotency, history, audit and
 * outbox included). Nothing here writes to a table.
 * <p>
 * Tres Ríos works mostly on washing machines and refrigerators; San Pedro sees more microwaves,
 * televisions and home visits. All names, phones (0555-01xx: not a Costa Rican numbering range in
 * use), e-mails (example.test) and addresses are fictitious.
 */
final class DemoScenario {

	private static final String CONSENT_TEXT = NotificationConsentText.CURRENT_VERSION;

	/** The public form records the caller's address only for its in-memory rate limit. */
	private static final String FORM_CLIENT_ADDRESS = "127.0.0.1";

	private final DemoServices s;

	private final DemoActors a;

	private final DemoCalendar cal;

	private final DemoTimeline timeline = new DemoTimeline();

	private final Map<String, Long> productIds = new HashMap<>();

	private int operations;

	// ---- Customers (fictitious) ----

	private final Person andrea = person("Andrea Vargas Quesada", "0555-0101", "andrea.vargas@example.test",
			"Tres Ríos centro", consent(IN_PERSON, EMAIL));

	private final Person carlos = person("Carlos Jiménez Mora", "0555-0102", "carlos.jimenez@example.test",
			"Concepción de Tres Ríos", consent(IN_PERSON, EMAIL, WHATSAPP));

	private final Person oscar = person("Óscar Navarro Pérez", "0555-0103", null, "San Diego de Tres Ríos", null);

	private final Person esteban = person("Esteban Castillo Rojas", "0555-0104", "esteban.castillo@example.test",
			"Tres Ríos centro", null);

	private final Person daniel = person("Daniel Mora Calderón", "0555-0105", null, "San Juan de Tres Ríos",
			consent(IN_PERSON, WHATSAPP));

	private final Person gabriela = person("Gabriela Umaña Soto", "0555-0106", "gabriela.umana@example.test",
			"Concepción de Tres Ríos", consent(IN_PERSON, EMAIL));

	private final Person silvia = person("Silvia Montero Brenes", "0555-0107", "silvia.montero@example.test",
			"San Diego de Tres Ríos", consent(IN_PERSON, EMAIL));

	private final Person luisDiego = person("Luis Diego Araya Mata", "0555-0108", "luisdiego.araya@example.test",
			"Curridabat", consent(IN_PERSON, EMAIL));

	private final Person jorge = person("Jorge Arturo Céspedes", "0555-0109", null, "Tres Ríos centro", null);

	private final Person rosa = new Person("Rosa Elena Picado", "0555-0110", null, "San Juan de Tres Ríos",
			"Prefiere que la llamen después de las 3 p. m.", null);

	private final Person mariaFernanda = person("María Fernanda Solano", "0555-0111", "mfernanda.solano@example.test",
			"Tres Ríos centro", consent(IN_PERSON, EMAIL));

	private final Person patricia = person("Patricia Leiva Alfaro", "0555-0112", "patricia.leiva@example.test", null,
			null);

	private final Person roberto = person("Roberto Chinchilla Vega", "0555-0113", "roberto.chinchilla@example.test",
			"San Juan de Tres Ríos", consent(PHONE, EMAIL));

	private final Person laura = person("Laura Castro Fonseca", "0555-0114", "laura.castro@example.test", null, null);

	private final Person hannia = person("Hannia Barboza Mata", "0555-0115", "hannia.barboza@example.test", null, null);

	private final Person sofia = person("Sofía Hernández Bonilla", "0555-0116", "sofia.hernandez@example.test",
			"San Pedro centro", consent(IN_PERSON, EMAIL));

	private final Person natalia = person("Natalia Rodríguez Vega", "0555-0117", "natalia.rodriguez@example.test",
			"Sabanilla", consent(IN_PERSON, EMAIL, WHATSAPP));

	private final Person mauricio = new Person("Mauricio Brenes Salas", "0555-0118", null, "Curridabat",
			"Cliente frecuente; solo contacto por teléfono.", null);

	private final Person karla = person("Karla Méndez Obando", "0555-0119", "karla.mendez@example.test",
			"San Rafael de Montes de Oca", consent(IN_PERSON, EMAIL));

	private final Person lucia = person("Lucía Campos Retana", "0555-0120", "lucia.campos@example.test",
			"San Pedro centro", consent(IN_PERSON, EMAIL));

	private final Person josePablo = person("José Pablo Sánchez", "0555-0121", "josepablo.sanchez@example.test",
			"San Pedro centro", consent(IN_PERSON, EMAIL));

	private final Person alejandro = person("Alejandro Zúñiga Porras", "0555-0122", "alejandro.zuniga@example.test",
			"Mercedes de Montes de Oca", consent(WRITTEN, EMAIL));

	private final Person valeria = person("Valeria Chaves Arias", "0555-0123", "valeria.chaves@example.test", null, null);

	private final Person melissa = person("Melissa Arce Villalobos", "0555-0124", "melissa.arce@example.test", null, null);

	private final Person fernando = person("Fernando Quirós Vindas", "0555-0125", null, null, null);

	private final Person monica = person("Mónica Jara Solís", "0555-0126", "monica.jara@example.test", null, null);

	private final Person adriana = person("Adriana Sibaja Ramírez", "0555-0127", "adriana.sibaja@example.test", null,
			null);

	private final Person ricardo = person("Ricardo Solís Madrigal", "0555-0128", null, "San Pedro centro",
			consent(PHONE, WHATSAPP));

	private final Person daniela = person("Daniela Pacheco Rojas", "0555-0129", "daniela.pacheco@example.test", null,
			null);

	private final Person priscilla = person("Priscilla Gómez Artavia", "0555-0130", "priscilla.gomez@example.test", null,
			null);

	/** Contacts of requests that never became customers (still pending, or declined). */
	private final Person minor = person("Minor Chacón Ureña", "0555-0131", null, null, null);

	private final Person allan = person("Allan Vargas Cordero", "0555-0132", null, null, null);

	// ---- Places: general areas only, never a real house ----

	private static final Place TRES_RIOS = new Place(Province.CARTAGO, "La Unión", "Tres Ríos",
			"Sector central, referencia ficticia.");

	private static final Place CONCEPCION = new Place(Province.CARTAGO, "La Unión", "Concepción",
			"Zona residencial al norte, referencia ficticia.");

	private static final Place SAN_DIEGO = new Place(Province.CARTAGO, "La Unión", "San Diego",
			"Cerca de la plaza de deportes, referencia ficticia.");

	private static final Place SAN_JUAN = new Place(Province.CARTAGO, "La Unión", "San Juan",
			"Calle principal, referencia ficticia.");

	private static final Place SAN_PEDRO = new Place(Province.SAN_JOSE, "Montes de Oca", "San Pedro",
			"Sector central, referencia ficticia.");

	private static final Place SABANILLA = new Place(Province.SAN_JOSE, "Montes de Oca", "Sabanilla",
			"Zona residencial, referencia ficticia.");

	private static final Place SAN_RAFAEL = new Place(Province.SAN_JOSE, "Montes de Oca", "San Rafael",
			"Cerca del centro comercial, referencia ficticia.");

	private static final Place MERCEDES = new Place(Province.SAN_JOSE, "Montes de Oca", "Mercedes",
			"Frente a zona verde, referencia ficticia.");

	private static final Place CURRIDABAT = new Place(Province.SAN_JOSE, "Curridabat", "Curridabat",
			"Sector central, referencia ficticia.");

	DemoScenario(DemoServices services, DemoActors actors, DemoCalendar calendar) {
		this.s = services;
		this.a = actors;
		this.cal = calendar;
	}

	DemoTimeline build() {
		setup();
		catalog();
		inventory();
		tresRiosWorkshop();
		sanPedroWorkshop();
		homeService();
		return timeline;
	}

	// ---- Week 1: the branches start using the system ----

	private void setup() {
		LocalDate first = cal.day(-54);
		CurrentUser admin = a.jefferson();
		single(cal.at(first, "07:40"), admin, "Configuración del portal público", user -> {
			PortalSettingsView current = s.portal().settings();
			s.portal().update(user, valid(new PortalSettingsRequest(true, true, true, 1, 30, List.of(1, 2, 3, 4, 5, 6),
					List.of(Province.SAN_JOSE, Province.CARTAGO, Province.HEREDIA),
					List.of("Lavadora", "Refrigeradora", "Secadora", "Cocina eléctrica", "Horno eléctrico", "Microondas",
							"Televisor", "Dispensador de agua"),
					"Solicite la visita de un técnico. Revisamos cada solicitud y le confirmamos la fecha por teléfono o correo.",
					"Recibimos su solicitud. Le contactaremos para confirmar la fecha y la hora de la visita.",
					current.version())));
		});
		single(cal.at(first, "07:50"), admin, "Parámetros de visitas de San Pedro",
				user -> s.schedules().updateSettings(user, a.sanPedro(), valid(new ServiceSettingsRequest(60, 30))));
		single(cal.at(first, "07:55"), admin, "Horario de Julián",
				user -> s.schedules().replaceWeek(user, a.julian().id(), valid(week(a.tresRios(), "12:00"))));
		single(cal.at(first, "07:58"), admin, "Horario de Pedro",
				user -> s.schedules().replaceWeek(user, a.pedro().id(), valid(week(a.sanPedro(), "13:00"))));
	}

	/** Monday to Friday 08:00-17:00 with lunch; Saturday morning. */
	private static WeekShiftsRequest week(long branchId, String saturdayEnd) {
		List<ShiftInput> days = new ArrayList<>();
		for (int day = 1; day <= 5; day++) {
			days.add(new ShiftInput(day, branchId, LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0),
					LocalTime.of(13, 0)));
		}
		days.add(new ShiftInput(6, branchId, LocalTime.of(8, 0), LocalTime.parse(saturdayEnd), null, null));
		return new WeekShiftsRequest(days);
	}

	/**
	 * Every article is created with the units already on the shelf of the branch that holds most of
	 * them (BR-INV-008); the afternoon count of the other branch is a receipt, and the next morning
	 * each branch gets its minimums.
	 */
	private void catalog() {
		LocalDate first = cal.day(-54);
		LocalTime created = LocalTime.of(8, 5);
		LocalTime counted = LocalTime.of(13, 30);
		LocalTime minimums = LocalTime.of(8, 0);
		for (DemoCatalog.Item item : DemoCatalog.ITEMS) {
			boolean tresRiosHolds = item.tresRios() >= item.sanPedro();
			long home = tresRiosHolds ? a.tresRios() : a.sanPedro();
			long other = tresRiosHolds ? a.sanPedro() : a.tresRios();
			int homeUnits = tresRiosHolds ? item.tresRios() : item.sanPedro();
			int otherUnits = tresRiosHolds ? item.sanPedro() : item.tresRios();
			single(cal.at(first, created.toString()), a.jefferson(), "Alta de " + item.sku(), user -> {
				ProductResponse product = s.catalog().create(user, valid(new CreateProductRequest(item.sku(), item.name(),
						item.category(), item.description(), item.kind(), new BigDecimal(item.cost()),
						new BigDecimal(item.price()), item.chargeable(), new InitialStock(home, homeUnits))));
				productIds.put(item.sku(), product.id());
			});
			movement(cal.at(first, counted.toString()), other, item.sku(), RECEIPT, otherUnits,
					"Conteo inicial de existencias de la sucursal");
			minimum(cal.at(cal.day(-53), minimums.toString()), a.tresRios(), item.sku(), item.minTresRios());
			minimum(cal.at(cal.day(-53), minimums.plusMinutes(1).toString()), a.sanPedro(), item.sku(),
					item.minSanPedro());
			created = created.plusMinutes(4);
			counted = counted.plusMinutes(3);
			minimums = minimums.plusMinutes(2);
		}
	}

	// ---- Receipts, counter sales, count corrections and transfers ----

	private void inventory() {
		long tr = a.tresRios();
		long sp = a.sanPedro();
		transfer(-49, "10:30", tr, sp, "CAP-035UF", 3, "Traslado solicitado por sucursal San Pedro");
		movement(-46, "10:10", tr, "BOM-DES-01", RECEIPT, 2, "Reposición de repuestos de lavadora");
		movement(-44, "15:10", tr, "PROT-VOLT-REF", ISSUE, 2, "Venta en mostrador");
		movement(-43, "10:20", sp, "CTRL-UNI-TV", ISSUE, 2, "Venta en mostrador");
		transfer(-42, "09:15", sp, tr, "NTC-10K", 3, "Reposición de sensores para reparaciones de refrigeración");
		movement(-41, "15:45", sp, "CAB-HDMI-2M", ISSUE, 3, "Venta en mostrador");
		movement(-40, "09:30", sp, "CAB-HDMI-2M", RECEIPT, 10, "Recepción de inventario semanal");
		movement(-39, "11:05", tr, "CAB-HDMI-2M", ISSUE, 2, "Venta en mostrador");
		movement(-38, "11:30", sp, "ADA-12V-2A", ISSUE, 2, "Venta en mostrador");
		movement(-37, "16:20", tr, "PROT-VOLT-REF", ISSUE, 1, "Venta en mostrador");
		movement(-36, "09:20", tr, "ROD-6205", ADJUSTMENT_OUT, 1, "Corrección por conteo físico: unidad con sello dañado");
		transfer(-35, "11:45", tr, sp, "CAB-HDMI-2M", 4, "Balanceo de existencias para venta en San Pedro");
		movement(-32, "10:50", tr, "CAB-ALI-18", ISSUE, 2, "Venta en mostrador");
		movement(-31, "16:10", sp, "PROT-VOLT-REF", ISSUE, 2, "Venta en mostrador");
		movement(-30, "10:15", sp, "MSW-MIC-01", RECEIPT, 10, "Recepción de inventario semanal");
		transfer(-29, "10:05", sp, tr, "TAR-TV-FTE", 1, "Existencia de respaldo para el taller de Tres Ríos");
		movement(-28, "14:15", tr, "REG-PROT-6", ISSUE, 1, "Venta en mostrador");
		movement(-27, "15:20", sp, "CAB-HDMI-2M", ISSUE, 4, "Venta en mostrador");
		movement(-26, "09:45", tr, "PROT-VOLT-REF", RECEIPT, 6, "Recepción de inventario semanal");
		movement(-24, "10:35", sp, "REG-PROT-6", ISSUE, 2, "Venta en mostrador");
		movement(-22, "08:50", sp, "CON-COAX-F", ADJUSTMENT_IN, 2, "Corrección por conteo físico");
		transfer(-22, "14:30", tr, sp, "ROD-6205", 2, "Traslado solicitado por sucursal San Pedro");
		movement(-21, "15:30", tr, "PROT-VOLT-REF", ISSUE, 3, "Venta en mostrador");
		movement(-20, "15:20", sp, "ADA-12V-2A", ISSUE, 3, "Venta en mostrador");
		single(cal.at(cal.day(-20), "09:05"), a.jefferson(), "Nuevo precio de MAG-MIC-01", user -> {
			long id = productIds.get("MAG-MIC-01");
			ProductResponse current = s.catalog().detail(user, id).product();
			s.catalog().update(user, id, valid(new UpdateProductRequest(current.name(), current.category(),
					current.description(), current.kind(), new BigDecimal("18200.00"), new BigDecimal("32500.00"), true,
					true, current.version())));
		});
		movement(-19, "11:20", tr, "CAP-035UF", RECEIPT, 6, "Reposición de capacitores de arranque");
		movement(-18, "11:50", sp, "CON-COAX-F", ISSUE, 2, "Venta en mostrador");
		movement(-17, "11:40", tr, "VAL-ENT-LAV", ISSUE, 1, "Venta de repuesto en mostrador");
		transfer(-16, "09:50", sp, tr, "CTRL-UNI-TV", 5, "Traslado solicitado por sucursal Tres Ríos");
		movement(-15, "16:40", tr, "CAB-HDMI-2M", ADJUSTMENT_OUT, 1, "Corrección por conteo físico");
		movement(-14, "16:05", tr, "PROT-VOLT-REF", ISSUE, 4, "Venta en mostrador");
		movement(-13, "10:10", sp, "CAB-HDMI-2M", ISSUE, 2, "Venta en mostrador");
		movement(-12, "09:40", sp, "FUS-CER-15A", RECEIPT, 10, "Recepción de inventario semanal");
		movement(-11, "10:25", tr, "CAB-HDMI-2M", ISSUE, 1, "Venta en mostrador");
		movement(-11, "09:55", sp, "MAG-MIC-01", ISSUE, 1, "Venta de repuesto a técnico independiente");
		transfer(-10, "15:10", tr, sp, "FUS-10A", 4, "Reposición de fusibles térmicos");
		movement(-9, "13:30", tr, "CTRL-UNI-TV", ISSUE, 1, "Venta en mostrador");
		movement(-9, "14:40", sp, "CTRL-UNI-TV", ISSUE, 1, "Venta en mostrador");
		movement(-8, "10:05", tr, "KIT-CON-01", RECEIPT, 4, "Reposición de consumibles de taller");
		movement(-7, "11:15", tr, "PROT-VOLT-REF", ISSUE, 2, "Venta en mostrador");
		movement(-6, "09:05", sp, "PLA-MIC-27", ADJUSTMENT_OUT, 1, "Plato quebrado al manipularlo en bodega");
		movement(-6, "10:30", sp, "REG-PROT-6", ISSUE, 3, "Venta en mostrador");
		movement(-5, "09:35", sp, "REG-PROT-6", RECEIPT, 6, "Recepción de inventario semanal");
		transfer(-4, "10:45", sp, tr, "MSW-MIC-01", 3, "Reposición de microinterruptores");
		movement(-3, "15:50", sp, "CAB-HDMI-2M", ISSUE, 5, "Venta en mostrador");
		transfer(-2, "14:05", tr, sp, "COR-LAV-01", 1, "Repuesto para una orden en diagnóstico en San Pedro");
		movement(-2, "16:15", sp, "ADA-12V-2A", ISSUE, 2, "Venta en mostrador");
		movement(-1, "09:10", tr, "TER-REF-01", RECEIPT, 2, "Pedido a proveedor de refrigeración");
		movement(-1, "10:40", tr, "PROT-VOLT-REF", ISSUE, 1, "Venta en mostrador");
	}

	// ---- Tres Ríos workshop (Susan at the counter, Julián at the bench) ----

	private void tresRiosWorkshop() {
		CurrentUser susan = a.susan();
		CurrentUser julian = a.julian();
		long tr = a.tresRios();

		order("Lavadora de Andrea", tr)
			.receive(-50, "09:14", susan, andrea, false, device("Lavadora", "LG", "WT16WSB", "WT16-7K3301"),
					"No centrifuga y queda agua dentro del tambor.",
					"Rayones leves en la tapa superior; panel de control en buen estado.", "Manguera de desagüe")
			.assign(-50, "10:02", julian)
			.move(-49, "08:35", julian, DIAGNOSING)
			.diagnose(-49, "09:50", julian,
					"Se encontró la bomba de desagüe bloqueada y con desgaste en el eje. El filtro de la bomba estaba obstruido con residuos.")
			.quote(-49, "10:05", julian, "24900.00",
					"Cambio de bomba de desagüe y filtro, limpieza de ductos y prueba de centrifugado.")
			.decide(-48, "11:20", susan, APPROVED, DecisionMethod.PHONE, "La clienta aprueba por teléfono.")
			.move(-47, "08:20", julian, IN_REPAIR)
			.part(-47, "09:10", julian, "BOM-DES-01", 1)
			.part(-47, "09:13", julian, "FIL-LAV-01", 1)
			.move(-47, "15:40", julian, READY_FOR_PICKUP)
			.move(-45, "10:30", susan, DELIVERED);

		order("Refrigeradora de Carlos", tr)
			.receive(-44, "08:47", susan, carlos, false, device("Refrigeradora", "Whirlpool", "WRT311FZDM", "WR31-2P0978"),
					"Enfría por algunas horas y luego deja de trabajar.",
					"Puerta con una abolladura pequeña en la esquina inferior.", null)
			.assign(-44, "09:30", julian)
			.move(-44, "13:10", julian, DIAGNOSING)
			.diagnose(-43, "10:20", julian,
					"Sensor NTC del evaporador con lectura intermitente y motor ventilador con ruido en el rodamiento. Compresor y gas en buen estado.")
			.quote(-43, "10:35", julian, "32000.00",
					"Cambio de sensor NTC y motor ventilador del evaporador, descongelamiento y prueba de 24 horas.")
			.decide(-42, "15:05", susan, APPROVED, DecisionMethod.IN_PERSON, null)
			.move(-41, "08:15", julian, IN_REPAIR)
			.part(-41, "09:05", julian, "NTC-10K", 1)
			.part(-41, "09:40", julian, "MOT-VEN-REF", 1)
			.move(-40, "16:20", julian, READY_FOR_PICKUP)
			.move(-38, "09:40", susan, DELIVERED);

		order("Secadora de Óscar", tr)
			.receive(-40, "10:05", susan, oscar, false, device("Secadora", "Frigidaire", "FFRE4120SW", "FR41-0K2215"),
					"Enciende pero no calienta.", "Óxido en la base de la carcasa; puerta y perilla en buen estado.", null)
			.assign(-40, "10:40", julian)
			.move(-39, "08:30", julian, DIAGNOSING)
			.diagnose(-38, "09:15", julian,
					"Resistencia abierta y tarjeta de control con pistas quemadas por humedad. El fabricante ya no distribuye esta tarjeta.")
			.move(-38, "11:00", julian, UNREPAIRABLE, "Tarjeta de control descontinuada; no hay repuesto disponible.")
			.move(-36, "14:15", susan, DELIVERED);

		order("Televisor de Esteban", tr)
			.receive(-34, "11:30", susan, esteban, false, device("Televisor", "Samsung", "UN55TU7000", "0B7K3CNR500412"),
					"Tiene sonido pero la pantalla se queda en negro.", "Marco sin golpes; se recibe con su base.",
					"Base de mesa y control remoto")
			.assign(-34, "11:55", julian)
			.move(-33, "08:40", julian, DIAGNOSING)
			.diagnose(-33, "11:10", julian,
					"La retroiluminación funciona; el panel presenta daño en la matriz (líneas verticales al iluminarlo).")
			.quote(-33, "11:25", julian, "68000.00",
					"Sustitución del panel de 55 pulgadas, sujeta a disponibilidad del proveedor.")
			.decide(-32, "10:05", susan, REJECTED, DecisionMethod.PHONE, "El cliente prefiere comprar un televisor nuevo.")
			.move(-31, "16:00", susan, DELIVERED);

		order("Microondas de Daniel", tr)
			.receive(-29, "09:25", susan, daniel, false, device("Microondas", "Panasonic", "NN-ST45KW", "6B21-0345"),
					"El plato gira y la luz enciende, pero no calienta los alimentos.",
					"Puerta y bisagras en buen estado; interior con manchas de uso.", "Plato giratorio")
			.assign(-29, "10:00", julian)
			.move(-28, "08:30", julian, DIAGNOSING)
			.diagnose(-28, "09:45", julian,
					"Fusible de alta tensión abierto y capacitor de alta tensión fuera de tolerancia. Magnetrón en buen estado.")
			.move(-28, "10:10", julian, IN_REPAIR)
			.part(-28, "10:40", julian, "CAP-HV-1UF", 1)
			.part(-28, "10:45", julian, "FUS-CER-15A", 1)
			.move(-27, "14:30", julian, READY_FOR_PICKUP)
			.move(-26, "11:15", susan, DELIVERED);

		order("Horno de Andrea", tr)
			.receive(-23, "15:20", susan, andrea, true, device("Horno eléctrico", "Oster", "TSSTTVDGXL", null),
					"Se apaga después de aproximadamente 15 minutos.",
					"Vidrio de la puerta con manchas de grasa; sin golpes.", "Bandeja y parrilla")
			.assign(-23, "15:50", julian)
			.move(-22, "08:45", julian, DIAGNOSING)
			.diagnose(-22, "10:30", julian,
					"El termostato de seguridad se dispara porque la resistencia inferior está deteriorada; cableado interno reseco cerca de la resistencia.")
			.quote(-22, "10:40", julian, "18500.00", "Cambio de resistencia inferior y del tramo de cableado dañado.")
			.decide(-21, "09:20", susan, APPROVED, DecisionMethod.EMAIL, "Aprobación recibida por correo.")
			.move(-21, "10:30", julian, IN_REPAIR)
			.part(-21, "11:00", julian, "RES-HOR-01", 1)
			.part(-21, "11:05", julian, "KIT-CON-01", 1)
			.move(-20, "15:10", julian, READY_FOR_PICKUP)
			.move(-19, "10:45", susan, DELIVERED)
			.giveBack(-18, "09:00", a.jefferson(), "KIT-CON-01", 1,
					"Revisión de cierre: el kit no se abrió y vuelve a bodega.");

		order("Lavadora de Gabriela", tr)
			.receive(-9, "09:20", susan, gabriela, false, device("Lavadora", "Whirlpool", "WWI16BSHLA", "WP16-3C7710"),
					"La clienta indica un ruido fuerte durante el centrifugado.",
					"Carcasa en buen estado; tapa con rayones leves.", "Mangueras de entrada y desagüe")
			.assign(-9, "09:55", julian)
			.move(-8, "08:40", julian, DIAGNOSING)
			.diagnose(-8, "10:15", julian,
					"Rodamientos 6204 y 6205 del tambor con juego excesivo; la correa se revisará al desarmar. Motor en buen estado.")
			.quote(-8, "10:30", julian, "38500.00",
					"Cambio de rodamientos 6204 y 6205, sellos y correa si se requiere; prueba de centrifugado.")
			.decide(-7, "11:10", susan, APPROVED, DecisionMethod.IN_PERSON, null)
			.move(-6, "08:30", julian, IN_REPAIR)
			.part(-6, "09:15", julian, "ROD-6204", 1)
			.part(-6, "09:20", julian, "ROD-6205", 1)
			.part(-6, "09:25", julian, "COR-LAV-01", 1)
			.quote(-5, "11:00", julian, "12500.00",
					"Trabajo adicional: cambio del interruptor de tapa, dañado; se detectó al desarmar el equipo.")
			.decide(-4, "10:20", susan, APPROVED, DecisionMethod.PHONE, "La clienta aprueba el trabajo adicional.")
			.move(-4, "14:00", julian, IN_REPAIR)
			.part(-3, "08:50", julian, "INT-PUE-LAV", 1)
			.giveBack(-3, "09:30", julian, "COR-LAV-01", 1,
					"Al desarmar, la correa original estaba en buen estado; la nueva vuelve a bodega.");

		order("Refrigeradora de Silvia", tr)
			.receive(-6, "10:40", susan, silvia, false, device("Refrigeradora", "Mabe", "RMP736FJCU", "MB73-5F1102"),
					"No enfría la parte de abajo; el congelador sí funciona.",
					"Empaque de la puerta inferior algo desgastado; sin golpes.", null)
			.assign(-6, "11:15", julian)
			.move(-5, "08:35", julian, DIAGNOSING)
			.diagnose(-5, "09:40", julian,
					"Motor ventilador del evaporador sin giro; escarcha acumulada en los ductos de aire.")
			.quote(-5, "09:55", julian, "27300.00", "Cambio de motor ventilador del evaporador y descongelamiento de ductos.")
			.decide(-3, "10:10", susan, APPROVED, DecisionMethod.PHONE, null)
			.move(-2, "08:20", julian, IN_REPAIR)
			.part(-2, "09:00", julian, "MOT-VEN-REF", 1);

		order("Lavadora de Luis Diego", tr)
			.receive(-8, "14:10", susan, luisDiego, false, device("Lavadora", "Samsung", "WA17T6260BY", "SA17-9D4420"),
					"Pierde agua durante el ciclo de lavado.", "Panel y tapa en buen estado.", "Manguera de entrada")
			.assign(-8, "14:40", julian)
			.move(-7, "08:35", julian, DIAGNOSING)
			.diagnose(-7, "09:20", julian, "Válvula de entrada con fisura en el cuerpo y manguera de entrada deteriorada.")
			.quote(-7, "09:35", julian, "15750.00", "Cambio de válvula de entrada y manguera de 1,5 m; prueba de llenado.")
			.decide(-6, "16:05", susan, APPROVED, DecisionMethod.EMAIL, null)
			.move(-5, "08:10", julian, IN_REPAIR)
			.part(-5, "08:45", julian, "VAL-ENT-LAV", 1)
			.part(-5, "08:50", julian, "MAN-ENT-15", 1)
			.move(-2, "11:30", julian, READY_FOR_PICKUP);

		order("Cocina de Jorge", tr)
			.receive(-10, "10:15", susan, jorge, false, device("Cocina eléctrica", "Atlas", "CE-3040", null),
					"Dos hornillas no calientan.", "Superficie con manchas; perillas completas.", null)
			.assign(-10, "10:50", julian)
			.move(-9, "08:30", julian, DIAGNOSING)
			.diagnose(-9, "10:00", julian,
					"Resistencia de la hornilla trasera izquierda abierta; selector de la hornilla delantera con contactos sulfatados (se limpió).")
			.quote(-9, "10:15", julian, "22400.00",
					"Cambio de resistencia espiral, limpieza de selectores y revisión del cableado.")
			.then(-7, "15:30", susan, "correo del cliente", user -> {
				CustomerResponse current = s.customers().get(user, jorge.id);
				s.customers().update(user, jorge.id, valid(new UpdateCustomerRequest(current.fullName(), current.phone(),
						"jorge.cespedes@example.test", current.address(), current.internalNotes(), current.version())));
			})
			.then(-7, "15:32", susan, "aviso por correo aceptado", user -> s.consents().record(user, jorge.id,
					valid(new RecordConsentRequest(EMAIL, true, PHONE, CONSENT_TEXT))))
			.decide(-7, "15:35", susan, APPROVED, DecisionMethod.PHONE,
					"El cliente aprueba por teléfono y registra su correo para recibir avisos.")
			.move(-6, "13:20", julian, IN_REPAIR)
			.part(-6, "13:50", julian, "RES-HOR-ESP", 1)
			.part(-6, "13:55", julian, "KIT-CON-01", 1)
			.move(-1, "10:40", julian, READY_FOR_PICKUP);

		order("Refrigeradora de Rosa", tr)
			.receive(-3, "09:05", susan, rosa, false, device("Refrigeradora", "LG", "GT32BPP", "LG32-4B8813"),
					"Hace ruido y la luz interior parpadea.", "Puerta del congelador con un rayón visible.", null)
			.assign(-3, "09:40", julian)
			.move(-2, "08:15", julian, DIAGNOSING)
			.diagnose(-2, "10:30", julian,
					"Tarjeta de control con el relé del compresor dañado; se recomienda instalar una tarjeta universal.")
			.quote(-2, "10:45", julian, "47500.00",
					"Instalación de tarjeta electrónica universal, ajuste de sensores y prueba de funcionamiento.");

		order("Extractor de María Fernanda", tr)
			.receive(-1, "11:40", susan, mariaFernanda, false, device("Extractor", "Teka", "DBB 60", null),
					"El motor no arranca y se percibe olor a quemado.",
					"Filtros metálicos con grasa acumulada; carcasa sin golpes.", "Filtros metálicos")
			.assign(-1, "11:55", julian)
			.move(0, "08:25", julian, DIAGNOSING)
			.diagnose(0, "11:30", julian, "Motor con el bobinado abierto; se consulta precio del motor de reemplazo.");

		order("Lavadora de Carlos", tr)
			.receive(0, "08:35", susan, carlos, true, device("Lavadora", "Mabe", "LMA79113VBAB0", "MB79-1A6604"),
					"No enciende; el panel no muestra ninguna luz.",
					"Carcasa en buen estado; se recibe sin la tapa del dispensador.", null)
			.assign(0, "09:00", julian)
			.move(0, "10:20", julian, DIAGNOSING);

		order("Microondas de Óscar", tr)
			.receive(0, "13:45", susan, oscar, true, device("Microondas", "Sankey", "MW-25", null),
					"El panel se reinicia solo y el horno se apaga a los pocos segundos.",
					"Cierre de la puerta algo flojo; interior limpio.", "Plato giratorio");

		order("Dispensador de Luis Diego", tr)
			.receive(0, "14:50", susan, luisDiego, true, device("Dispensador de agua", "Oster", null, null),
					"No enfría el agua; solo sale a temperatura ambiente.", "Bandeja de goteo con sarro; sin golpes.", null)
			.assign(0, "15:10", julian);
	}

	// ---- San Pedro workshop (Maicol at the counter, Pedro at the bench) ----

	private void sanPedroWorkshop() {
		CurrentUser maicol = a.maicol();
		CurrentUser pedro = a.pedro();
		long sp = a.sanPedro();

		order("Microondas de Sofía", sp)
			.receive(-51, "10:20", maicol, sofia, false, device("Microondas", "Samsung", "MS23K3513AS", "SM23-8K0291"),
					"Hace chispas dentro del horno al calentar.", "Interior con manchas de grasa; puerta en buen estado.",
					"Plato giratorio y aro")
			.assign(-51, "10:45", pedro)
			.move(-50, "08:40", pedro, DIAGNOSING)
			.diagnose(-50, "09:30", pedro, "Mica protectora de la guía de ondas quemada y magnetrón con desgaste en la antena.")
			.quote(-50, "09:45", pedro, "34500.00", "Cambio de magnetrón y mica protectora, limpieza de la cavidad.")
			.decide(-49, "12:05", maicol, APPROVED, DecisionMethod.PHONE, null)
			.move(-48, "08:30", pedro, IN_REPAIR)
			.part(-48, "09:20", pedro, "MAG-MIC-01", 1)
			.move(-48, "14:10", pedro, READY_FOR_PICKUP)
			.move(-46, "16:40", maicol, DELIVERED);

		order("Televisor de Natalia", sp)
			.receive(-45, "11:10", maicol, natalia, false, device("Televisor", "TCL", "43S5400", "TC43-2R7750"),
					"No responde al control remoto.", "Pantalla sin rayones; se recibe con su base.", "Control remoto")
			.assign(-45, "11:30", pedro)
			.move(-44, "09:00", pedro, DIAGNOSING)
			.diagnose(-44, "10:10", pedro,
					"Receptor infrarrojo con falso contacto por humedad; se resoldó. El control remoto tiene los contactos sulfatados.")
			.move(-44, "10:30", pedro, IN_REPAIR)
			.part(-44, "11:00", pedro, "KIT-CON-01", 1)
			.move(-44, "15:30", pedro, READY_FOR_PICKUP)
			.move(-43, "10:15", maicol, DELIVERED);

		order("Televisor de Carlos", sp)
			.receive(-27, "09:50", maicol, carlos, true, device("Televisor", "Sony", "KD-50X75K", "SO50-6T1184"),
					"Se apaga solo después de unos minutos de uso.", "Marco con rayones leves; pantalla sin daños.",
					"Base de mesa")
			.assign(-27, "10:20", pedro)
			.move(-26, "08:30", pedro, DIAGNOSING)
			.diagnose(-26, "09:40", pedro, "Fuente de poder con capacitores inflados; tarjeta principal en buen estado.")
			.quote(-26, "09:55", pedro, "29900.00", "Cambio de fuente de poder y prueba de funcionamiento continuo.")
			.decide(-26, "14:30", maicol, APPROVED, DecisionMethod.PHONE, null)
			.move(-25, "08:15", pedro, IN_REPAIR)
			.part(-25, "09:00", pedro, "TAR-TV-FTE", 1)
			.move(-25, "15:45", pedro, READY_FOR_PICKUP)
			.move(-24, "11:30", maicol, DELIVERED);

		order("Horno de Mauricio", sp)
			.receive(-9, "16:10", maicol, mauricio, false, device("Horno eléctrico", "Black+Decker", "TO3250XSB", null),
					"El temporizador no funciona y el horno no se apaga solo.",
					"Vidrio de la puerta con una grieta pequeña en la esquina.", "Bandeja")
			.assign(-9, "16:30", pedro)
			.move(-8, "09:10", pedro, DIAGNOSING)
			.diagnose(-8, "10:40", pedro,
					"Temporizador mecánico dañado; el repuesto original tiene un costo alto frente al valor del equipo.")
			.quote(-8, "10:55", pedro, "18900.00", "Cambio de temporizador original y revisión de contactos.")
			.decide(-4, "11:45", maicol, REJECTED, DecisionMethod.PHONE,
					"El cliente decide no reparar; retirará el equipo esta semana.");

		order("Ventilador de Karla", sp)
			.receive(-4, "15:15", maicol, karla, false, device("Ventilador", "Oster", "OFT4200", null),
					"Gira muy lento y hace un zumbido.", "Rejilla con polvo acumulado; sin golpes.", "Control remoto")
			.assign(-4, "15:40", pedro)
			.move(-3, "08:30", pedro, DIAGNOSING)
			.diagnose(-3, "09:00", pedro, "Capacitor de arranque fuera de tolerancia; motor y cableado en buen estado.")
			.move(-3, "09:20", pedro, IN_REPAIR)
			.part(-3, "09:45", pedro, "CAP-VEN-1.5UF", 1)
			.move(-3, "11:10", pedro, READY_FOR_PICKUP);

		order("Microondas de Lucía", sp)
			.receive(-5, "10:05", maicol, lucia, false, device("Microondas", "LG", "MS1146S", "LG11-7M3350"),
					"No enciende; se escuchó un chasquido y se apagó.", "Puerta y panel en buen estado.", "Plato giratorio")
			.assign(-5, "10:30", pedro)
			.move(-4, "08:20", pedro, DIAGNOSING)
			.diagnose(-4, "09:10", pedro, "Fusible cerámico abierto por la falla de un microinterruptor de la puerta.")
			.quote(-4, "09:25", pedro, "12000.00", "Cambio de fusible y microinterruptores de puerta; prueba de seguridad.")
			.decide(-3, "13:40", maicol, APPROVED, DecisionMethod.PHONE, null)
			.move(-2, "08:40", pedro, IN_REPAIR)
			.part(-2, "09:10", pedro, "FUS-CER-15A", 1)
			.part(-2, "09:15", pedro, "MSW-MIC-01", 2)
			.giveBack(-1, "09:30", pedro, "MSW-MIC-01", 1,
					"Solo fue necesario cambiar un microinterruptor; el segundo vuelve a bodega.");

		order("Lavadora de José Pablo", sp)
			.receive(-3, "11:30", maicol, josePablo, false, device("Lavadora", "Samsung", "WA13T5260BY", "SA13-3H9021"),
					"No centrifuga; el tambor gira con dificultad.", "Carcasa con rayones en el costado.",
					"Manguera de desagüe")
			.assign(-3, "11:50", pedro)
			.move(-2, "08:50", pedro, DIAGNOSING)
			.diagnose(-2, "09:40", pedro, "Correa desgastada y rodamiento 6205 con ruido; tambor y motor sin daños.")
			.quote(-2, "09:55", pedro, "21800.00",
					"Cambio de correa y rodamiento 6205, lubricación y prueba de centrifugado.");

		order("Dispensador de Alejandro", sp)
			.receive(-2, "10:10", maicol, alejandro, false, device("Dispensador de agua", "Whirlpool", "WK5012Q", null),
					"No enfría el agua y el compresor arranca y se detiene.", "Carcasa en buen estado; sin botellón.", null)
			.assign(-2, "10:30", pedro)
			.move(-1, "08:30", pedro, DIAGNOSING)
			.diagnose(-1, "09:20", pedro, "Relé de arranque del compresor defectuoso y termostato descalibrado.")
			.quote(-1, "09:35", pedro, "16400.00",
					"Cambio de relé de arranque, calibración del termostato y prueba de enfriamiento.")
			.decide(0, "10:15", maicol, APPROVED, DecisionMethod.IN_PERSON, null);

		order("Televisor de Sofía", sp)
			.receive(0, "10:40", maicol, sofia, true, device("Televisor", "Hisense", "32A4H", "HS32-1Q5580"),
					"Tiene imagen pero no tiene sonido.", "Pantalla sin rayones; se recibe sin base.", "Control remoto")
			.assign(0, "11:05", pedro);
	}

	// ---- Home service: requests, visits and the technicians' agenda ----

	private void homeService() {
		CurrentUser maicol = a.maicol();
		CurrentUser susan = a.susan();
		CurrentUser pedro = a.pedro();
		CurrentUser julian = a.julian();
		long sp = a.sanPedro();
		long tr = a.tresRios();

		LocalDate v = cal.weekday(-33);
		request("Visita a Valeria", valeria, sp)
			.submitted(cal.at(cal.day(v, -2), "19:20"), SAN_RAFAEL, "Refrigeradora", "Samsung", null,
					"La refrigeradora hace un ruido fuerte al arrancar el compresor.", v, MORNING, true, false)
			.linkNew(cal.at(cal.day(v, -1), "08:45"), maicol)
			.visit(cal.at(cal.day(v, -1), "08:55"), maicol, pedro, v, "10:00", true)
			.start(cal.at(v, "10:04"), pedro)
			.complete(cal.at(v, "10:58"), pedro, RESOLVED_ON_SITE,
					"Soportes del compresor flojos; se ajustaron y se limpió el condensador. Funcionamiento normal.");

		v = cal.weekday(-26);
		request("Visita a Natalia", natalia, sp)
			.byStaff(cal.at(cal.day(v, -2), "11:20"), maicol, true, SABANILLA, "Lavadora", "LG", null,
					"Pierde agua por la parte de abajo al terminar el ciclo.", v, AFTERNOON)
			.visit(cal.at(cal.day(v, -2), "11:30"), maicol, pedro, v, "13:00", false)
			.confirm(cal.at(cal.day(v, -1), "09:15"), maicol)
			.start(cal.at(v, "13:03"), pedro)
			.complete(cal.at(v, "13:52"), pedro, RESOLVED_ON_SITE,
					"Manguera de desagüe mal acoplada; se reacomodó y se ajustó la abrazadera. Sin fugas en ciclo completo.");

		v = cal.weekday(-19);
		request("Visita a Patricia", patricia, sp)
			.submitted(cal.at(cal.day(v, -3), "20:15"), CONCEPCION, "Lavadora", "LG", null,
					"La lavadora no arranca; solo se escucha un zumbido.", v, MORNING, true, false)
			.moveTo(cal.at(cal.day(v, -2), "08:30"), a.jefferson(), tr)
			.linkNew(cal.at(cal.day(v, -2), "09:10"), susan)
			.visit(cal.at(cal.day(v, -2), "09:25"), susan, julian, v, "10:00", true)
			.start(cal.at(v, "10:05"), julian)
			.complete(cal.at(v, "11:20"), julian, RESOLVED_ON_SITE,
					"Había una moneda atascada entre la tina y el tambor; se retiró y el motor funciona normalmente.");

		v = cal.weekday(-10);
		Request robertoVisit = request("Visita a Roberto", roberto, tr);
		robertoVisit.then(cal.at(cal.day(v, -2), "14:05"), susan, "registro del cliente",
				user -> roberto.id = s.customers().create(user, valid(new CreateCustomerRequest(tr, roberto.asNew()))).id());
		robertoVisit
			.byStaff(cal.at(cal.day(v, -2), "14:10"), susan, true, SAN_JUAN, "Refrigeradora", "Mabe", null,
					"La refrigeradora gotea agua dentro del compartimento de verduras.", v, AFTERNOON)
			.visit(cal.at(cal.day(v, -2), "14:20"), susan, julian, v, "13:00", true)
			.start(cal.at(v, "13:04"), julian)
			.complete(cal.at(v, "14:25"), julian, RESOLVED_ON_SITE,
					"Drenaje del evaporador obstruido con hielo; se destapó y se verificó el desagüe.");

		LocalDate first = cal.weekday(-8);
		LocalDate moved = cal.weekday(-7);
		request("Visita a Melissa", melissa, sp)
			.submitted(cal.at(cal.day(first, -3), "20:05"), CURRIDABAT, "Cocina eléctrica", "Whirlpool", null,
					"El horno de la cocina no calienta de forma pareja y tarda mucho en llegar a la temperatura.", first,
					MORNING, true, false)
			.review(cal.at(cal.day(first, -2), "08:55"), maicol)
			.linkNew(cal.at(cal.day(first, -2), "09:10"), maicol)
			.visit(cal.at(cal.day(first, -2), "09:20"), maicol, pedro, first, "10:00", true)
			.reschedule(cal.at(cal.day(first, -1), "14:35"), maicol, pedro, moved, "16:00",
					"La clienta pidió pasar la visita al día siguiente por la tarde.")
			.start(cal.at(moved, "16:02"), pedro)
			.complete(cal.at(moved, "16:55"), pedro, RESOLVED_ON_SITE,
					"Sensor de temperatura con el conector sulfatado; se limpió y aseguró la conexión. Calentamiento uniforme verificado.");

		request("Solicitud de Allan", allan, sp)
			.submitted(cal.at(cal.day(-6), "19:40"), CURRIDABAT, "Refrigeradora", "Imbera", null,
					"La cámara de refrigeración del negocio no mantiene la temperatura.", cal.day(-4), MORNING, false,
					false)
			.reject(cal.at(cal.day(-5), "08:50"), maicol,
					"Se trata de una cámara de refrigeración comercial; no atendemos equipos de uso industrial.");

		v = cal.weekday(-5);
		request("Visita a Fernando", fernando, sp)
			.byStaff(cal.at(cal.day(v, -2), "10:05"), maicol, false, MERCEDES, "Lavadora", "Mabe", null,
					"La lavadora no llena de agua; se detiene al inicio del ciclo.", v, MORNING)
			.visit(cal.at(cal.day(v, -2), "10:15"), maicol, pedro, v, "08:30", true)
			.start(cal.at(v, "08:31"), pedro)
			.complete(cal.at(v, "09:25"), pedro, NOT_RESOLVED,
					"La vivienda no tiene presión de agua suficiente; la lavadora funciona bien en otra toma. Se recomendó revisar la instalación.");

		v = cal.weekday(-4);
		Request monicaVisit = request("Visita a Mónica", monica, sp)
			.byStaff(cal.at(cal.day(v, -2), "12:10"), maicol, false, SAN_PEDRO, "Secadora", "Samsung", null,
					"La secadora hace un ruido metálico al girar.", v, AFTERNOON);
		monicaVisit
			.then(cal.at(cal.day(v, -2), "12:12"), maicol, "aviso por correo aceptado",
					user -> s.consents().record(user, monica.id, valid(new RecordConsentRequest(EMAIL, true, PHONE,
							CONSENT_TEXT))))
			.visit(cal.at(cal.day(v, -2), "12:20"), maicol, pedro, v, "14:30", true)
			.cancelVisit(cal.at(cal.day(v, -1), "09:40"), maicol,
					"La clienta viajará esta semana; llamará para coordinar una nueva fecha.");

		v = cal.weekday(-1);
		Order microwave = new Order("Microondas de Adriana", sp);
		request("Visita a Adriana", adriana, sp)
			.submitted(cal.at(cal.day(v, -3), "21:10"), MERCEDES, "Microondas", "Oster", null,
					"El microondas enciende pero no calienta y hace un zumbido fuerte.", v, AFTERNOON, true, false)
			.review(cal.at(cal.day(v, -2), "08:20"), maicol)
			.linkNew(cal.at(cal.day(v, -2), "08:30"), maicol)
			.visit(cal.at(cal.day(v, -2), "08:40"), maicol, pedro, v, "14:30", true)
			.start(cal.at(v, "14:33"), pedro)
			.complete(cal.at(v, "15:20"), pedro, NEEDS_WORKSHOP,
					"El magnetrón no genera calor; se requiere revisión en taller.")
			.toWorkshop(cal.at(v, "16:05"), maicol,
					"Equipo retirado por el técnico durante la visita; carcasa y puerta en buen estado.",
					"Plato giratorio y aro de soporte", microwave);
		microwave.assign(cal.at(cal.day(v, 1), "09:00"), pedro).move(cal.at(cal.day(0), "09:10"), pedro, DIAGNOSING);

		v = cal.weekday(0);
		Request ricardoVisit = request("Visita a Ricardo", ricardo, sp);
		ricardoVisit.then(cal.at(cal.day(v, -2), "11:40"), maicol, "registro del cliente",
				user -> ricardo.id = s.customers().create(user, valid(new CreateCustomerRequest(sp, ricardo.asNew()))).id());
		ricardoVisit
			.byStaff(cal.at(cal.day(v, -2), "11:45"), maicol, true, SAN_PEDRO, "Refrigeradora", "Frigidaire", null,
					"La refrigeradora no enfría y el compresor hace clic cada pocos minutos.", v, MORNING)
			.visit(cal.at(cal.day(v, -2), "11:55"), maicol, pedro, v, "08:30", true)
			.start(cal.at(v, "08:32"), pedro)
			.complete(cal.at(v, "09:35"), pedro, RESOLVED_ON_SITE,
					"Relé de arranque del compresor con falso contacto; se limpió y aseguró la conexión. Enfriamiento normal a los 30 minutos.");

		request("Visita a Daniela", daniela, sp)
			.submitted(cal.at(cal.day(v, -2), "18:45"), SABANILLA, "Lavadora", "Whirlpool", null,
					"La lavadora se detiene a mitad del ciclo y muestra un código de error en el panel.", v, AFTERNOON, true,
					false)
			.linkNew(cal.at(cal.day(v, -1), "09:05"), maicol)
			.visit(cal.at(cal.day(v, -1), "09:15"), maicol, pedro, v, "16:00", true)
			.start(cal.at(v, "16:03"), pedro)
			.complete(cal.at(v, "16:58"), pedro, RESOLVED_ON_SITE,
					"Filtro de la bomba obstruido; se limpió y se reinició el panel. Ciclo completo sin errores.");

		request("Visita a Laura", laura, tr)
			.submitted(cal.at(cal.day(v, -3), "19:50"), TRES_RIOS, "Lavadora", "Mabe", null,
					"La lavadora vibra demasiado y se desplaza durante el centrifugado.", v, AFTERNOON, true, false)
			.linkNew(cal.at(cal.day(v, -2), "08:40"), susan)
			.visit(cal.at(cal.day(v, -2), "08:50"), susan, julian, v, "13:00", true)
			.start(cal.at(v, "13:04"), julian)
			.complete(cal.at(v, "14:30"), julian, RESOLVED_ON_SITE,
					"Se niveló la lavadora y se ajustaron las patas; la vibración desapareció en la prueba de centrifugado.");

		request("Solicitud de Karla", karla, sp)
			.submitted(cal.at(cal.day(0), "07:15"), SAN_RAFAEL, "Microondas", "Mabe", null,
					"El microondas empotrado enciende la luz pero no calienta.", cal.day(1), MORNING, true, false);

		request("Solicitud de Minor", minor, tr)
			.submitted(cal.at(cal.day(0), "06:40"), TRES_RIOS, "Secadora", "LG", null,
					"La secadora gira pero no calienta la ropa.", cal.day(2), MORNING, false, false);

		// Upcoming: this week's agenda.
		v = cal.weekday(1);
		request("Visita a Priscilla", priscilla, sp)
			.submitted(cal.at(cal.day(-1), "20:30"), SAN_PEDRO, "Microondas", "Panasonic", null,
					"El microondas hace un ruido fuerte y el plato no gira.", v, MORNING, true, false)
			.linkNew(cal.at(cal.day(0), "08:50"), maicol)
			.visit(cal.at(cal.day(0), "09:00"), maicol, pedro, v, "10:00", true);

		request("Visita a Carlos", carlos, tr)
			.byStaff(cal.at(cal.day(0), "09:40"), susan, true, CONCEPCION, "Refrigeradora", "Whirlpool", null,
					"La segunda refrigeradora de la casa no mantiene la temperatura.", v, MORNING)
			.visit(cal.at(cal.day(0), "09:50"), susan, julian, v, "08:00", true);

		v = cal.weekday(2);
		request("Visita a Hannia", hannia, tr)
			.submitted(cal.at(cal.day(-1), "17:30"), SAN_DIEGO, "Horno eléctrico", "Oster", null,
					"La puerta del horno no cierra bien y se pierde el calor.", v, MORNING, true, true)
			.linkNew(cal.at(cal.day(0), "08:15"), susan)
			.visit(cal.at(cal.day(0), "08:25"), susan, julian, v, "10:00", true);

		v = cal.weekday(3);
		request("Visita a José Pablo", josePablo, sp)
			.submitted(cal.at(cal.day(-1), "13:20"), SAN_PEDRO, "Refrigeradora", "LG", null,
					"Se forma hielo en la parte trasera del compartimento de alimentos.", v, AFTERNOON, true, false)
			.linkExisting(cal.at(cal.day(0), "11:20"), maicol, josePablo)
			.visit(cal.at(cal.day(0), "11:30"), maicol, pedro, v, "14:30", false);
	}

	// ---- Building blocks ----

	private void single(Instant at, CurrentUser actor, String label, Consumer<CurrentUser> action) {
		timeline.add(at, actor, label, () -> action.accept(actor));
	}

	private void movement(int day, String time, long branchId, String sku, MovementType type, int quantity,
			String reason) {
		movement(cal.at(cal.day(day), time), branchId, sku, type, quantity, reason);
	}

	private void movement(Instant at, long branchId, String sku, MovementType type, int quantity, String reason) {
		UUID operationId = operation("movement");
		single(at, a.jefferson(), type + " " + sku, user -> s.movements().record(user, valid(new RecordMovementRequest(
				operationId, branchId, productIds.get(sku), type, quantity, reason))));
	}

	private void transfer(int day, String time, long from, long to, String sku, int quantity, String reason) {
		UUID operationId = operation("transfer");
		single(cal.at(cal.day(day), time), a.jefferson(), "Transferencia de " + sku,
				user -> s.transfers().transfer(user, valid(new CreateTransferRequest(operationId, from, to,
						productIds.get(sku), quantity, reason))));
	}

	private void minimum(Instant at, long branchId, String sku, int minimum) {
		single(at, a.jefferson(), "Mínimo de " + sku,
				user -> s.stock().changeMinimum(user, branchId, productIds.get(sku), minimum));
	}

	private Order order(String label, long branchId) {
		return new Order(label, branchId);
	}

	private Request request(String label, Person contact, long branchId) {
		return new Request(label, contact, branchId);
	}

	/** Client-side operation ids (one per user intent), deterministic so a fresh load is reproducible. */
	private UUID operation(String kind) {
		return UUID.nameUUIDFromBytes(("electronica-rojas:" + kind + ":" + (++operations))
			.getBytes(StandardCharsets.UTF_8));
	}

	/** The Jakarta constraints a controller applies with {@code @Valid}. */
	private <T> T valid(T request) {
		Set<ConstraintViolation<T>> violations = s.validator().validate(request);
		if (!violations.isEmpty()) {
			throw new IllegalArgumentException(request.getClass().getSimpleName() + " is invalid: " + violations.stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining(", ")));
		}
		return request;
	}

	private static Person person(String name, String phone, String email, String address, ConsentGrant consent) {
		return new Person(name, phone, email, address, null, consent);
	}

	private static ConsentGrant consent(ConsentSource source, ContactChannel... channels) {
		return new ConsentGrant(List.of(channels), source, CONSENT_TEXT);
	}

	private static Device device(String type, String brand, String model, String serial) {
		return new Device(type, brand, model, serial);
	}

	private record Device(String type, String brand, String model, String serial) {
	}

	private record Place(Province province, String canton, String district, String address) {
	}

	/** A customer (or a request contact); {@code id} is known once a use case registered them. */
	private static final class Person {

		final String name;

		final String phone;

		final String email;

		final String address;

		final String notes;

		final ConsentGrant consent;

		Long id;

		Person(String name, String phone, String email, String address, String notes, ConsentGrant consent) {
			this.name = name;
			this.phone = phone;
			this.email = email;
			this.address = address;
			this.notes = notes;
			this.consent = consent;
		}

		NewCustomer asNew() {
			return new NewCustomer(name, phone, email, address, notes, null, consent);
		}

	}

	/**
	 * Operations on one record, in order. Each step must come after the previous one: a scenario
	 * mistake fails while building, before anything is written.
	 */
	private abstract class Chain<C extends Chain<C>> {

		final String label;

		private Instant last;

		Chain(String label) {
			this.label = label;
		}

		@SuppressWarnings("unchecked")
		C then(Instant at, CurrentUser actor, String what, Consumer<CurrentUser> action) {
			if (last != null && !at.isAfter(last)) {
				throw new IllegalStateException(label + ": '" + what + "' is not after the previous step");
			}
			last = at;
			timeline.add(at, actor, label + ": " + what, () -> action.accept(actor));
			return (C) this;
		}

		C then(int day, String time, CurrentUser actor, String what, Consumer<CurrentUser> action) {
			return then(cal.at(cal.day(day), time), actor, what, action);
		}

		void startsAfter(Instant at) {
			last = at;
		}

	}

	/** A repair order through the workshop. */
	private final class Order extends Chain<Order> {

		private final long branchId;

		private Long id;

		Order(String label, long branchId) {
			super(label);
			this.branchId = branchId;
		}

		/** @param existing true when the customer is already registered (else created at the counter) */
		Order receive(int day, String time, CurrentUser who, Person customer, boolean existing, Device device,
				String fault, String condition, String accessories) {
			UUID operationId = operation("intake");
			return then(day, time, who, "recepción", user -> {
				RepairOrderDetail order = s.repairs().receive(user, valid(new CreateRepairOrderRequest(operationId,
						branchId, existing ? customer.id : null, existing ? customer.phone : null,
						existing ? null : customer.asNew(), device.type(), device.brand(), device.model(),
						device.serial(), fault, condition, accessories)));
				id = order.id();
				customer.id = order.customer().id();
			});
		}

		/** Management assigns the bench technician. */
		Order assign(int day, String time, CurrentUser technician) {
			return assign(cal.at(cal.day(day), time), technician);
		}

		Order assign(Instant at, CurrentUser technician) {
			return then(at, a.jefferson(), "asignación de técnico",
					user -> s.repairs().assignTechnician(user, id, valid(new AssignTechnicianRequest(technician.id()))));
		}

		Order move(int day, String time, CurrentUser who, RepairStatus target) {
			return move(cal.at(cal.day(day), time), who, target);
		}

		Order move(Instant at, CurrentUser who, RepairStatus target) {
			return then(at, who, "estado " + target,
					user -> s.repairs().transition(user, id, valid(new TransitionRequest(target, null))));
		}

		Order move(int day, String time, CurrentUser who, RepairStatus target, String reason) {
			return then(day, time, who, "estado " + target,
					user -> s.repairs().transition(user, id, valid(new TransitionRequest(target, reason))));
		}

		/** Like the order page: read the current version, then save the diagnosis with it. */
		Order diagnose(int day, String time, CurrentUser who, String text) {
			return then(day, time, who, "diagnóstico", user -> s.repairs().updateDiagnosis(user, id,
					valid(new DiagnosisRequest(text, s.repairs().get(user, id).version()))));
		}

		Order quote(int day, String time, CurrentUser who, String amount, String description) {
			return then(day, time, who, "cotización",
					user -> s.quotes().create(user, id, valid(new CreateQuoteRequest(new BigDecimal(amount), description))));
		}

		Order decide(int day, String time, CurrentUser who, QuoteDecision decision, DecisionMethod method, String note) {
			return then(day, time, who, "decisión de cotización", user -> {
				long quoteId = s.repairs().get(user, id).quotes().stream()
					.filter(quote -> quote.status() == QuoteStatus.PENDING)
					.findFirst()
					.orElseThrow()
					.id();
				s.quotes().decide(user, id, quoteId, valid(new QuoteDecisionRequest(decision, method, note)));
			});
		}

		/** A spare part of the order's branch, with the catalog's price and charge (the technician's form). */
		Order part(int day, String time, CurrentUser who, String sku, int quantity) {
			UUID operationId = operation("part");
			return then(day, time, who, "repuesto " + sku, user -> s.parts().consume(user, id,
					valid(new ConsumePartRequest(operationId, productIds.get(sku), quantity, null, null, null))));
		}

		/** Units of a consumed part back to the shelf, with the reason (BR-REP-012). */
		Order giveBack(int day, String time, CurrentUser who, String sku, int quantity, String reason) {
			UUID operationId = operation("part-return");
			return then(day, time, who, "devolución " + sku, user -> {
				long usageId = s.repairs().get(user, id).parts().stream()
					.filter(line -> line.product().sku().equals(sku) && line.remainingQuantity() >= quantity)
					.findFirst()
					.orElseThrow()
					.id();
				s.parts().returnPart(user, id, usageId, valid(new ReturnPartRequest(operationId, quantity, reason)));
			});
		}

	}

	/** A home-service request and its visit. */
	private final class Request extends Chain<Request> {

		private final Person contact;

		private long branchId;

		private Long id;

		private String code;

		private Long visitId;

		Request(String label, Person contact, long branchId) {
			super(label);
			this.contact = contact;
			this.branchId = branchId;
		}

		/** The customer fills in the public form (anonymous). */
		Request submitted(Instant at, Place place, String type, String brand, String model, String problem,
				LocalDate preferred, PreferredWindow window, boolean emailNotices, boolean whatsappNotices) {
			UUID submissionId = operation("submission");
			long branch = branchId;
			return then(at, null, "solicitud en el portal", user -> code = s.publicRequests()
				.submit(valid(new PublicSubmission(submissionId, branch, contact.name, contact.phone, contact.email,
						place.province(), place.canton(), place.district(), place.address(), type, brand, model, problem,
						preferred, window, null, true, emailNotices, whatsappNotices,
						emailNotices || whatsappNotices ? CONSENT_TEXT : null, null)), FORM_CLIENT_ADDRESS)
				.requestCode());
		}

		/** Taken by phone or at the counter, for a registered customer or registering a new one. */
		Request byStaff(Instant at, CurrentUser who, boolean existingCustomer, Place place, String type, String brand,
				String model, String problem, LocalDate preferred, PreferredWindow window) {
			UUID submissionId = operation("submission");
			long branch = branchId;
			return then(at, who, "solicitud registrada por el personal", user -> {
				ServiceRequestDetail detail = s.requests().createByStaff(user, valid(new StaffRequest(submissionId, branch,
						existingCustomer ? contact.id : null, existingCustomer ? null : Boolean.TRUE, null, contact.name,
						contact.phone, contact.email, place.province(), place.canton(), place.district(), place.address(),
						type, brand, model, problem, preferred, window, null, null)));
				id = detail.id();
				code = detail.requestCode();
				contact.id = detail.customer().id();
			});
		}

		Request review(Instant at, CurrentUser who) {
			return then(at, who, "revisión", user -> s.requests().startReview(user, requestId(user)));
		}

		/** Identity resolution: nobody matches, the contact becomes a new customer. */
		Request linkNew(Instant at, CurrentUser who) {
			return then(at, who, "cliente nuevo", user -> contact.id = s.requests()
				.linkCustomer(user, requestId(user), valid(new LinkCustomerRequest(null, true, null)))
				.customer()
				.id());
		}

		/** Identity resolution: the phone belongs to a customer already registered. */
		Request linkExisting(Instant at, CurrentUser who, Person customer) {
			return then(at, who, "cliente existente", user -> s.requests()
				.linkCustomer(user, requestId(user), valid(new LinkCustomerRequest(customer.id, null, null))));
		}

		Request moveTo(Instant at, CurrentUser who, long branch) {
			return then(at, who, "cambio de sucursal", user -> {
				s.requests().changeBranch(user, requestId(user), valid(new ChangeBranchRequest(branch)));
				branchId = branch;
			});
		}

		Request reject(Instant at, CurrentUser who, String reason) {
			return then(at, who, "rechazo",
					user -> s.requests().reject(user, requestId(user), valid(new DecisionRequest(reason))));
		}

		Request visit(Instant at, CurrentUser who, CurrentUser technician, LocalDate day, String time, boolean confirm) {
			UUID operationId = operation("visit");
			return then(at, who, confirm ? "visita confirmada" : "visita propuesta", user -> visitId = s.visits()
				.schedule(user, requestId(user), valid(new ScheduleVisitRequest(operationId, technician.id(),
						DemoCalendar.slot(day, time), null, confirm)))
				.id());
		}

		Request confirm(Instant at, CurrentUser who) {
			return then(at, who, "confirmación", user -> s.visits().confirm(user, visitId));
		}

		Request reschedule(Instant at, CurrentUser who, CurrentUser technician, LocalDate day, String time,
				String reason) {
			return then(at, who, "reprogramación", user -> s.visits().reschedule(user, visitId,
					valid(new RescheduleVisitRequest(technician.id(), DemoCalendar.slot(day, time), null, reason))));
		}

		Request cancelVisit(Instant at, CurrentUser who, String reason) {
			return then(at, who, "visita cancelada",
					user -> s.visits().cancel(user, visitId, valid(new DecisionRequest(reason))));
		}

		Request start(Instant at, CurrentUser who) {
			return then(at, who, "inicio de visita", user -> s.visits().start(user, visitId));
		}

		Request complete(Instant at, CurrentUser who, VisitOutcome outcome, String notes) {
			return then(at, who, "cierre de visita",
					user -> s.visits().complete(user, visitId, valid(new CompleteVisitRequest(outcome, notes))));
		}

		/** The appliance goes to the workshop: a new order of the same customer, from the visit. */
		Request toWorkshop(Instant at, CurrentUser who, String condition, String accessories, Order order) {
			UUID operationId = operation("intake");
			order.startsAfter(at);
			return then(at, who, "paso a taller", user -> {
				VisitView visit = s.visits().linkRepairOrder(user, visitId,
						valid(new LinkRepairOrderRequest(null, operationId, condition, accessories)));
				order.id = visit.repairOrder().id();
			});
		}

		/** A public request is found in the inbox by its code, as the staff would. */
		private long requestId(CurrentUser user) {
			if (id == null) {
				id = s.requests().list(user, new RequestFilter(null, null, null, code), 0, 1).content().get(0).id();
			}
			return id;
		}

	}

}
