package uk.gov.justice.laa.dstew.access.utils;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Obtains signed test JWTs from the Docker or deployed {@code mock-oauth2-server} used by
 * infrastructure smoke tests.
 *
 * <p>Integration tests do not use this class; they mint tokens from the in-process mock server via
 * {@link TestTokenFactory}. This provider is only for tests running against a live API where the
 * mock OAuth2 server is reached over HTTP.
 */
public final class SmokeTestTokenProvider {

  private static final String DEFAULT_TOKEN_URL = "http://localhost:9998/entra/token";
  private static final String DEFAULT_SCOPE = "api://laa-data-access-api/.default";
  private static final String DEFAULT_CLIENT_ID = "test";
  private static final String DEFAULT_CLIENT_SECRET = "test";

  private static final String TOKEN_URL = setting("LAA_SMOKE_OAUTH_TOKEN_URL", DEFAULT_TOKEN_URL);
  private static final String CLIENT_ID = setting("OAUTH_CLIENT_ID", DEFAULT_CLIENT_ID);
  private static final String CLIENT_SECRET = setting("OAUTH_CLIENT_SECRET", DEFAULT_CLIENT_SECRET);
  private static final RestClient REST_CLIENT = RestClient.create();

  // Token expiry from mock server is 3600 seconds (1 hour). Refresh 5 minutes early.
  private static final long TOKEN_EXPIRY_MS = 3_600_000L;
  private static final long EXPIRY_BUFFER_MS = 300_000L;

  private static final Map<String, TokenCacheEntry> TOKEN_CACHE = new ConcurrentHashMap<>();

  private SmokeTestTokenProvider() {}

  /** Returns a cached or freshly fetched token with the {@code LAA_CASEWORKER} role. */
  public static String getCaseworkerToken() {
    long now = System.currentTimeMillis();
    TokenCacheEntry cached = TOKEN_CACHE.get(DEFAULT_SCOPE);
    if (cached != null && cached.isValidAt(now)) {
      return cached.token;
    }

    String token = fetchTokenFromServer();
    TOKEN_CACHE.put(DEFAULT_SCOPE, new TokenCacheEntry(token, now + TOKEN_EXPIRY_MS));
    return token;
  }

  private static String fetchTokenFromServer() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", CLIENT_ID);
    form.add("client_secret", CLIENT_SECRET);
    form.add("scope", DEFAULT_SCOPE);

    try {
      Map<?, ?> response =
          REST_CLIENT
              .post()
              .uri(URI.create(TOKEN_URL))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);

      Object accessToken = response == null ? null : response.get("access_token");
      if (!(accessToken instanceof String token) || token.isBlank()) {
        throw new IllegalStateException(
            "Mock OAuth server returned no access_token. Server URL: " + TOKEN_URL);
      }

      return token;
    } catch (RestClientException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "Failed to obtain token from mock OAuth server at "
              + TOKEN_URL
              + ". Is docker-compose.smoke-test.yml running?",
          e);
    }
  }

  private static String setting(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      value = System.getProperty(key);
    }
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private record TokenCacheEntry(String token, long expiryTime) {
    boolean isValidAt(long currentTimeMs) {
      return expiryTime > currentTimeMs + EXPIRY_BUFFER_MS;
    }
  }
}
