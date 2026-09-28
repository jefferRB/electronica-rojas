package dev.jeffrojas.electronicarojas.users;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional first administrator, supplied only through environment variables
 * (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD) in development or a controlled setup
 * (SEC-004). Nothing here has a default credential.
 */
@ConfigurationProperties("app.bootstrap.admin")
record AdminBootstrapProperties(String email, String password, @DefaultValue("Administrador") String fullName) {

	boolean isConfigured() {
		return email != null && !email.isBlank() && password != null && !password.isBlank();
	}

	/** Keeps the password out of logs, debuggers and error messages. */
	@Override
	public String toString() {
		return "AdminBootstrapProperties[email=" + email + ", password=***, fullName=" + fullName + "]";
	}

}
