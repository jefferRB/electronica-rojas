package dev.jeffrojas.electronicarojas.demo;

import static dev.jeffrojas.electronicarojas.inventory.ProductKind.MERCHANDISE;
import static dev.jeffrojas.electronicarojas.inventory.ProductKind.SPARE_PART;

import java.util.List;

import dev.jeffrojas.electronicarojas.inventory.ProductKind;

/**
 * Catalog of the demo shop: spare parts for the workshop and merchandise for the counter, with the
 * units each branch had when it started using the system and its minimum. Tres Ríos leans towards
 * washing machines and refrigerators, San Pedro towards microwaves, televisions and accessories.
 * Prices in colones (cost, suggested sale price); fictitious but plausible.
 */
final class DemoCatalog {

	/**
	 * @param tresRios units at SUC-001 on the first day; {@code sanPedro} the same at SUC-002
	 * @param chargeable whether a repair line charges the part by default (workshop consumables do not)
	 */
	record Item(String sku, String name, String category, String description, ProductKind kind, String cost,
			String price, boolean chargeable, int tresRios, int sanPedro, int minTresRios, int minSanPedro) {
	}

	static final List<Item> ITEMS = List.of(
			part("CAP-035UF", "Capacitor de arranque 35 µF 450 V", "Capacitores",
					"Para motores de lavadora y compresores pequeños.", "2350.00", "4500.00", 12, 6, 4, 3),
			part("CAP-045UF", "Capacitor de marcha 45 µF 450 V", "Capacitores", null, "2800.00", "5200.00", 8, 5, 3, 2),
			part("CAP-VEN-1.5UF", "Capacitor para ventilador 1,5 µF 250 V", "Capacitores",
					"Ventiladores de pedestal, torre y techo.", "650.00", "1500.00", 6, 10, 2, 4),
			part("CAP-HV-1UF", "Capacitor de alta tensión para microondas 1 µF 2100 V", "Microondas", null, "4100.00",
					"7900.00", 3, 6, 2, 3),
			part("FUS-10A", "Fusible térmico 10 A 216 °C", "Fusibles y protecciones", null, "450.00", "1200.00", 20, 10, 6,
					5),
			part("FUS-CER-15A", "Fusible cerámico 15 A para microondas", "Fusibles y protecciones", null, "300.00",
					"900.00", 8, 12, 3, 5),
			part("REL-UNI-01", "Relé universal de arranque para compresor", "Refrigeración", null, "1900.00", "3800.00", 6,
					4, 2, 2),
			part("NTC-10K", "Sensor de temperatura NTC 10 kΩ", "Sensores", "Refrigeradoras y lavadoras con control electrónico.",
					"1600.00", "3500.00", 5, 8, 3, 3),
			part("TER-REF-01", "Termostato para refrigeradora", "Refrigeración", null, "5900.00", "11500.00", 5, 3, 2, 2),
			part("MOT-VEN-REF", "Motor ventilador de evaporador para refrigeradora", "Refrigeración", null, "9800.00",
					"18900.00", 3, 2, 2, 1),
			part("BOM-DES-01", "Bomba de desagüe para lavadora 120 V", "Lavadoras", null, "7400.00", "14500.00", 5, 3, 2, 1),
			part("VAL-ENT-LAV", "Válvula doble de entrada de agua para lavadora", "Lavadoras", null, "5600.00", "10900.00",
					4, 4, 3, 1),
			part("COR-LAV-01", "Correa de transmisión para lavadora", "Lavadoras", null, "2300.00", "4800.00", 6, 3, 2, 1),
			part("ROD-6204", "Rodamiento 6204-2RS", "Rodamientos", null, "1800.00", "3600.00", 6, 4, 2, 1),
			part("ROD-6205", "Rodamiento 6205-2RS", "Rodamientos", null, "2100.00", "4200.00", 4, 3, 2, 1),
			part("INT-PUE-LAV", "Interruptor de tapa para lavadora", "Lavadoras", null, "2900.00", "5900.00", 4, 2, 2, 1),
			part("FIL-LAV-01", "Filtro de bomba para lavadora", "Lavadoras", null, "1400.00", "2900.00", 8, 3, 3, 1),
			part("MAN-ENT-15", "Manguera de entrada de agua 1,5 m", "Lavadoras", null, "2200.00", "4500.00", 10, 6, 3, 2),
			part("TAR-UNI-REF", "Tarjeta electrónica universal para refrigeradora", "Tarjetas electrónicas",
					"Reemplaza tarjetas de control de refrigeradoras de una y dos puertas.", "18500.00", "34900.00", 2, 1, 1,
					1),
			part("RES-HOR-01", "Resistencia calefactora para horno eléctrico 1500 W", "Resistencias", null, "6300.00",
					"12500.00", 4, 3, 2, 1),
			part("RES-HOR-ESP", "Resistencia espiral para hornilla de cocina 6\"", "Resistencias", null, "4800.00",
					"9500.00", 4, 2, 2, 1),
			part("RES-SEC-01", "Resistencia para secadora 5300 W", "Resistencias", null, "11200.00", "21500.00", 2, 2, 1, 1),
			part("MAG-MIC-01", "Magnetrón para microondas 1000 W", "Microondas", null, "16800.00", "29900.00", 1, 2, 1, 1),
			part("MSW-MIC-01", "Microinterruptor de puerta para microondas", "Microondas", null, "850.00", "2000.00", 6, 10,
					2, 4),
			part("PLA-MIC-27", "Plato giratorio de vidrio 27 cm", "Microondas", null, "3400.00", "6900.00", 2, 4, 1, 2),
			part("TAR-TV-FTE", "Fuente de poder para televisor LED de 32 a 50\"", "Televisores", null, "14500.00",
					"27500.00", 2, 3, 1, 2),
			new Item("KIT-CON-01", "Kit de conectores eléctricos surtidos", "Consumibles de taller",
					"Terminales, empalmes y funda termoencogible. Uso interno del taller.", SPARE_PART, "2600.00", "5500.00",
					false, 6, 5, 2, 2),
			goods("PROT-VOLT-REF", "Protector de voltaje para refrigeradora", "Protección eléctrica", "6900.00", "12900.00",
					10, 8, 4, 4),
			goods("REG-PROT-6", "Regleta con protección de 6 tomas", "Protección eléctrica", "4200.00", "8500.00", 8, 12, 3,
					4),
			goods("CAB-HDMI-2M", "Cable HDMI 2 m", "Cables y accesorios", "1700.00", "3900.00", 12, 20, 4, 6),
			goods("CTRL-UNI-TV", "Control remoto universal para televisor", "Accesorios", "2400.00", "4900.00", 6, 10, 3, 4),
			goods("CAB-ALI-18", "Cable de alimentación 3 × 16 AWG, 1,8 m", "Cables y accesorios", "1300.00", "2900.00", 10,
					8, 3, 3),
			goods("ADA-12V-2A", "Adaptador de corriente 12 V 2 A", "Fuentes y adaptadores", "2900.00", "5900.00", 5, 9, 2,
					3),
			goods("CON-COAX-F", "Conector coaxial tipo F (bolsa de 10)", "Cables y accesorios", "900.00", "2200.00", 15, 10,
					4, 3));

	private DemoCatalog() {
	}

	static List<String> skus() {
		return ITEMS.stream().map(Item::sku).toList();
	}

	private static Item part(String sku, String name, String category, String description, String cost, String price,
			int tresRios, int sanPedro, int minTresRios, int minSanPedro) {
		return new Item(sku, name, category, description, SPARE_PART, cost, price, true, tresRios, sanPedro, minTresRios,
				minSanPedro);
	}

	private static Item goods(String sku, String name, String category, String cost, String price, int tresRios,
			int sanPedro, int minTresRios, int minSanPedro) {
		return new Item(sku, name, category, null, MERCHANDISE, cost, price, true, tresRios, sanPedro, minTresRios,
				minSanPedro);
	}

}
