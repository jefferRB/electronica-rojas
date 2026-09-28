package dev.jeffrojas.electronicarojas.repairs;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.repairs.PartCharges.Line;
import dev.jeffrojas.electronicarojas.repairs.PartCharges.LinePricing;
import dev.jeffrojas.electronicarojas.repairs.PartCharges.Summary;

/** BR-REP-014: pricing decision of a part line and the totals of an order's parts. */
class PartChargesTest {

	private static BigDecimal crc(String amount) {
		return new BigDecimal(amount);
	}

	@Test
	void defaultsComeFromTheCatalog() {
		LinePricing pricing = PartCharges.decide(crc("12500.00"), true, null, null);

		assertThat(pricing.unitPrice()).isEqualByComparingTo("12500");
		assertThat(pricing.chargeable()).isTrue();
		assertThat(pricing.overridesDefaults()).isFalse();
	}

	@Test
	void sendingTheSameValuesIsNotAnOverride() {
		assertThat(PartCharges.decide(crc("12500.00"), true, crc("12500"), true).overridesDefaults()).isFalse();
	}

	@Test
	void anotherPriceOrChargingDecisionIsAnOverride() {
		LinePricing price = PartCharges.decide(crc("12500.00"), true, crc("10000.00"), null);
		assertThat(price.priceOverridden()).isTrue();
		assertThat(price.unitPrice()).isEqualByComparingTo("10000");

		LinePricing free = PartCharges.decide(crc("12500.00"), true, null, false);
		assertThat(free.chargeOverridden()).isTrue();
		assertThat(free.priceOverridden()).isFalse();
		// Not charged is not "price 0": the line keeps the price it would have had.
		assertThat(free.unitPrice()).isEqualByComparingTo("12500");

		// A price for a product without one is also a decision taken for this line.
		assertThat(PartCharges.decide(null, true, crc("5000"), null).priceOverridden()).isTrue();
	}

	@Test
	void chargedAmountUsesOnlyUnitsStillInUse() {
		assertThat(PartCharges.chargedAmount(new Line(2, crc("12500.00"), null, true))).isEqualByComparingTo("25000");
		assertThat(PartCharges.chargedAmount(new Line(0, crc("12500.00"), null, true))).isEqualByComparingTo("0");
		assertThat(PartCharges.chargedAmount(new Line(3, crc("12500.00"), null, false))).isEqualByComparingTo("0");
		assertThat(PartCharges.chargedAmount(new Line(1, null, null, true))).isNull();
	}

	@Test
	void summaryKeepsChargeableUnchargedAndUnpricedApart() {
		Summary summary = PartCharges.summarize(List.of(
				new Line(2, crc("12500.00"), crc("8000.00"), true),
				new Line(1, crc("4000.00"), crc("2500.00"), false),
				new Line(1, null, null, true),
				new Line(0, crc("99999.00"), crc("1.00"), true)), true);

		assertThat(summary.linesInUse()).isEqualTo(3);
		assertThat(summary.unitsInUse()).isEqualTo(4);
		assertThat(summary.unitsWithoutCharge()).isEqualTo(1);
		assertThat(summary.chargeableSubtotal()).isEqualByComparingTo("25000");
		assertThat(summary.unpricedLines()).isEqualTo(1);
		assertThat(summary.totalCost()).isEqualByComparingTo("18500");
		assertThat(summary.uncostedLines()).isEqualTo(1);
	}

	@Test
	void costStaysOutForRolesThatMayNotSeeIt() {
		Summary summary = PartCharges.summarize(List.of(new Line(2, crc("100.00"), crc("60.00"), true)), false);
		assertThat(summary.totalCost()).isNull();
		assertThat(summary.chargeableSubtotal()).isEqualByComparingTo("200");
	}

}
