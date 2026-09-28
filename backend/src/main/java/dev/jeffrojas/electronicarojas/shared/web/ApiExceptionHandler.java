package dev.jeffrojas.electronicarojas.shared.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Uniform RFC 9457 ProblemDetail responses (ENG-032, ARCH-API-002). Never includes stack traces
 * or SQL. Spring Security exceptions are intentionally NOT handled here so they reach the
 * security filter chain, which answers 401/403.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail conflict(ConflictException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		ex.properties().forEach(problem::setProperty);
		return problem;
	}

	/**
	 * A row lock could not be obtained (deadlock victim or lock timeout). The transaction was
	 * rolled back entirely, so the client can safely retry with the same operationId.
	 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ProblemDetail lockFailure(PessimisticLockingFailureException ex) {
		log.warn("Lock failure: {}", ex.getMostSpecificCause().getClass().getSimpleName());
		return conflict(new ConflictException("CONCURRENT_OPERATION",
				"The record is being changed by another operation. Retry the same request."));
	}

	@ExceptionHandler(InvalidRequestException.class)
	ProblemDetail invalid(InvalidRequestException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setProperty("errors",
				List.of(Map.of("field", ex.field(), "code", ex.code(), "message", ex.getMessage())));
		return problem;
	}

	/** Lost update detected by JPA {@code @Version} between our check and the commit. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail optimisticLock(ObjectOptimisticLockingFailureException ex) {
		return conflict(new ConflictException("STALE_VERSION",
				"The record was modified by someone else. Reload and try again."));
	}

	/** Neutral 429: says nothing about why (which limit, which phone or address). */
	@ExceptionHandler(RateLimitedException.class)
	ResponseEntity<ProblemDetail> rateLimited(RateLimitedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
		problem.setProperty("code", "RATE_LIMITED");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
			.body(problem);
	}

	/** A database constraint caught a race the service checks could not (e.g. duplicate code). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
		return conflict(new ConflictException("DATA_CONFLICT", "The data conflicts with an existing record."));
	}

	/** Adds per-field messages to Spring's default 400 for {@code @Valid} request bodies. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("The request contains invalid fields.");
		problem.setProperty("errors", ex.getBindingResult()
			.getFieldErrors()
			.stream()
			// code = constraint name (NotBlank, Size, Email...), translated by the UI.
			.map(error -> Map.of("field", error.getField(), "code", String.valueOf(error.getCode()), "message",
					String.valueOf(error.getDefaultMessage())))
			.toList());
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

}
