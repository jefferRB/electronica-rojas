package dev.jeffrojas.electronicarojas.customers;

/**
 * Published (synchronously, inside the recording transaction) after a consent statement is stored,
 * so other modules can react without the customers module depending on them. The notifications
 * module uses it to stop pending messages when a consent is withdrawn.
 */
public record ConsentChanged(long customerId, ContactChannel channel, boolean granted) {
}
