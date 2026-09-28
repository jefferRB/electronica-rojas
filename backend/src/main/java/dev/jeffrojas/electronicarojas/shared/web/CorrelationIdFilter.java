package dev.jeffrojas.electronicarojas.shared.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id (OBS-001): reused from a well-formed {@code X-Request-Id}
 * header or generated, put in the logging MDC and echoed in the response. Audit events store it
 * so a business action can be matched with its technical logs.
 * <p>
 * Runs before Spring Security, so even rejected requests are traceable. Client values are only
 * accepted if they are short and alphanumeric, which prevents log injection.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = "correlationId";

	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String correlationId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming
				: UUID.randomUUID().toString();
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER, correlationId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

}
