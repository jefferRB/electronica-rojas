package dev.jeffrojas.electronicarojas.inventory;

/**
 * Commercial goods versus spare parts for repairs. ASSUMPTION (ER-BR-001 section 11 pending):
 * each article has one main kind; consumption of spare parts by repair orders is a later phase
 * (BR-REP-007).
 */
public enum ProductKind {

	MERCHANDISE,
	SPARE_PART

}
