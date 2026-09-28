package dev.jeffrojas.electronicarojas.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditServiceTest {

	@Test
	void summariesAreSingleLineAndBounded() {
		assertThat(AuditService.sanitize("  Transfer\nof 4\r\tunits  ")).isEqualTo("Transfer of 4 units");
		assertThat(AuditService.sanitize("x".repeat(500))).hasSize(300).endsWith("…");
	}

}
