package dev.jeffrojas.electronicarojas.customers;

/**
 * Channels a customer may accept notifications on (BR-CUS-001: consent per channel). Only EMAIL
 * is delivered in this phase; WHATSAPP consent is recorded for when a real integration exists.
 */
public enum ContactChannel {
	EMAIL, WHATSAPP
}
