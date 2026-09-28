package dev.jeffrojas.electronicarojas;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

/**
 * Minimal browser over real HTTP against the embedded Tomcat: keeps cookies (JSESSIONID,
 * XSRF-TOKEN) and echoes the CSRF cookie in X-XSRF-TOKEN like the React client. Thread-safe per
 * instance for sequential use; concurrency tests give each thread its own session.
 */
public final class BrowserClient implements AutoCloseable {

	public record Response(int status, String body) {
	}

	private final String baseUrl;

	private final CookieManager cookies = new CookieManager();

	private final HttpClient http;

	private BrowserClient(int port) {
		this.baseUrl = "http://localhost:" + port;
		this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
	}

	public static BrowserClient login(int port, String email, String password) throws IOException, InterruptedException {
		BrowserClient client = new BrowserClient(port);
		String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(password, StandardCharsets.UTF_8);
		HttpResponse<String> response = client.http.send(client.request("/api/v1/auth/login")
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("X-XSRF-TOKEN", client.csrfToken())
			.POST(BodyPublishers.ofString(form))
			.build(), BodyHandlers.ofString());
		if (response.statusCode() != 204) {
			throw new IllegalStateException("Login failed with HTTP " + response.statusCode());
		}
		return client;
	}

	public Response get(String path) throws IOException, InterruptedException {
		return send(request(path).GET().build());
	}

	public Response postJson(String path, String json) throws IOException, InterruptedException {
		return send(request(path).header("Content-Type", "application/json")
			.header("X-XSRF-TOKEN", csrfToken())
			.POST(BodyPublishers.ofString(json))
			.build());
	}

	private Response send(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
		return new Response(response.statusCode(), response.body());
	}

	/** Reads the XSRF-TOKEN cookie, fetching it first if login/logout cleared it. */
	private String csrfToken() throws IOException, InterruptedException {
		String token = cookie("XSRF-TOKEN");
		if (token == null) {
			http.send(request("/api/v1/auth/csrf").GET().build(), BodyHandlers.discarding());
			token = cookie("XSRF-TOKEN");
		}
		return token;
	}

	private String cookie(String name) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(cookie -> cookie.getName().equals(name) && !cookie.getValue().isEmpty())
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path));
	}

	@Override
	public void close() {
		http.close();
	}

}
