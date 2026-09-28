package dev.jeffrojas.electronicarojas.security;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Deny-by-default web security (ARCH-SEC-001..004).
 * <ul>
 * <li>Server-side HTTP session with the standard form-login filter (no JWT, no custom auth).</li>
 * <li>CSRF stays enabled, using Spring Security's SPA integration: an {@code XSRF-TOKEN}
 * cookie that React echoes back in the {@code X-XSRF-TOKEN} header.</li>
 * <li>Role checks with {@code @PreAuthorize}; branch scope checks live in the services.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	static final String LOGIN_URL = "/api/v1/auth/login";

	static final String LOGOUT_URL = "/api/v1/auth/logout";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService,
			LoginAttemptLimiter loginAttempts,
			@Value("${app.security.secure-cookies:false}") boolean secureCookies) throws Exception {
		CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));

		http
			.authorizeHttpRequests(auth -> auth
				// Let the container's error forward (/error) render the original status. Otherwise
				// e.g. a 403 CSRF rejection is re-authorized as anonymous and turns into a 401.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/ping", CsrfController.CSRF_URL).permitAll()
				// Home-service public form (Phase 4): list branches, submit (CSRF still required),
				// read one request's coarse status by its unguessable reference, and read the portal
				// page by its slug (BR-SRV-009; render data only). Nothing else: the portal settings
				// themselves (/api/v1/portal-settings) require ADMIN.
				.requestMatchers(HttpMethod.GET, "/api/v1/public/branches", "/api/v1/public/service-requests/*",
						"/api/v1/public/portal", "/api/v1/public/portal/*")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/public/service-requests").permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.formLogin(form -> form
				// Setting loginPage avoids Spring's generated HTML login page; the SPA owns the UI.
				.loginPage(LOGIN_URL)
				.loginProcessingUrl(LOGIN_URL)
				.usernameParameter("email")
				.passwordParameter("password")
				.successHandler((request, response, authentication) -> {
					loginAttempts.recordSuccess(request.getParameter("email"));
					SecurityResponses.loginSucceeded(request, response, authentication);
				})
				.failureHandler((request, response, exception) -> {
					loginAttempts.recordFailure(request.getParameter("email"), request.getRemoteAddr());
					SecurityResponses.loginFailed(request, response, exception);
				})
				.permitAll())
			.logout(logout -> logout
				.logoutUrl(LOGOUT_URL)
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
				.deleteCookies("JSESSIONID")
				.permitAll())
			// An API has no page to return to after login; saving requests would also create a
			// session for every anonymous 401.
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(SecurityResponses::unauthorized)
				.accessDeniedHandler(SecurityResponses::forbidden))
			.addFilterAfter(new SessionRevalidationFilter(userDetailsService), SecurityContextHolderFilter.class)
			// SEC-006: refuse attempts while blocked, before the password is even checked.
			.addFilterBefore(new LoginThrottleFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Delegating encoder: BCrypt today, stored as {@code {bcrypt}...} so the algorithm can be
	 * upgraded later without invalidating existing hashes (SEC-001).
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
