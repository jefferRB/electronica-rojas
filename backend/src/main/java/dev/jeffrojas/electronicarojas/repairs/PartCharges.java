package dev.jeffrojas.electronicarojas.repairs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Money of the spare parts used in a repair (BR-REP-014). Pure Java, unit-tested on its own.
 * <ul>
 * <li>A line keeps the price and cost it was recorded with; nothing here reads the catalog again.</li>
 * <li>Price and chargeability are separate: a part given away keeps its price and is simply not
 * charged ("Sin cargo"), it is never modelled as price 0.</li>
 * <li>Only the units still in use count: a return lowers the line's amount, it never rewrites it.</li>
 * </ul>
 * The parts subtotal is NOT added to a quote: a quote is an amount the customer approved as a whole
 * (BR-REP-010) and nothing says whether it already includes the parts.
 */
final class PartCharges {

	private PartCharges() {
	}

	/** What a new line will record, and whether it departs from the product's defaults. */
	record LinePricing(BigDecimal unitPrice, boolean chargeable, boolean priceOverridden, boolean chargeOverridden) {

		boolean overridesDefaults() {
			return priceOverridden || chargeOverridden;
		}

	}

	/**
	 * Decides the pricing of a new line. {@code requestedPrice} / {@code requestedChargeable} are
	 * what the user sent (null = take the default from the catalog).
	 */
	static LinePricing decide(BigDecimal catalogPrice, boolean chargeableByDefault, BigDecimal requestedPrice,
			Boolean requestedChargeable) {
		boolean chargeable = requestedChargeable == null ? chargeableByDefault : requestedChargeable;
		boolean priceOverridden = requestedPrice != null && !sameAmount(requestedPrice, catalogPrice);
		BigDecimal unitPrice = requestedPrice != null ? requestedPrice : catalogPrice;
		return new LinePricing(unitPrice, chargeable, priceOverridden, chargeable != chargeableByDefault);
	}

	/** The pricing snapshot of one line and how many of its units are still counted as used. */
	record Line(int unitsInUse, BigDecimal unitPrice, BigDecimal unitCost, boolean chargeable) {
	}

	/**
	 * What the line adds to the chargeable subtotal: 0 when it is not charged (or everything was
	 * returned), null when it is charged but has no price yet.
	 */
	static BigDecimal chargedAmount(Line line) {
		if (!line.chargeable() || line.unitsInUse() == 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		return line.unitPrice() == null ? null : line.unitPrice().multiply(BigDecimal.valueOf(line.unitsInUse()));
	}

	/**
	 * Totals of the order's parts. {@code withCost} = the caller may see internal costs; otherwise
	 * {@code totalCost} stays null.
	 */
	record Summary(int linesInUse, int unitsInUse, int unitsWithoutCharge, BigDecimal chargeableSubtotal,
			int unpricedLines, BigDecimal totalCost, int uncostedLines) {
	}

	static Summary summarize(List<Line> lines, boolean withCost) {
		int linesInUse = 0;
		int unitsInUse = 0;
		int unitsWithoutCharge = 0;
		int unpricedLines = 0;
		int uncostedLines = 0;
		BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
		BigDecimal cost = BigDecimal.ZERO.setScale(2);
		for (Line line : lines) {
			if (line.unitsInUse() == 0) {
				continue;
			}
			linesInUse++;
			unitsInUse += line.unitsInUse();
			if (!line.chargeable()) {
				unitsWithoutCharge += line.unitsInUse();
			}
			BigDecimal charged = chargedAmount(line);
			if (charged == null) {
				unpricedLines++;
			}
			else {
				subtotal = subtotal.add(charged);
			}
			if (line.unitCost() == null) {
				uncostedLines++;
			}
			else {
				cost = cost.add(line.unitCost().multiply(BigDecimal.valueOf(line.unitsInUse())));
			}
		}
		return new Summary(linesInUse, unitsInUse, unitsWithoutCharge, subtotal, unpricedLines,
				withCost ? cost : null, withCost ? uncostedLines : 0);
	}

	private static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return Objects.equals(a, b) || (a != null && b != null && a.compareTo(b) == 0);
	}

}
