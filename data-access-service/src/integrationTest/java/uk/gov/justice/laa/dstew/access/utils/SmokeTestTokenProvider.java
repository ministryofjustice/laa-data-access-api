package uk.gov.justice.laa.dstew.access.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Obtains test tokens from the mock-oauth2-server running in the smoke test infrastructure.
 *
 * <p>In infrastructure mode (smoke tests), the mock server runs as a Docker container. Tests call
 * this utility to fetch tokens via HTTP before making authenticated requests to the API.
 *
 * <p>The token URL defaults to {@code http://localhost:9998/entra/token} (the host port exposed by
 * docker-compose.smoke-test.yml) but can be overridden via the {@code LAA_SMOKE_OAUTH_TOKEN_URL}
 * environment variable for flexibility in CI/CD environments.
 *
 * <p>Tokens are cached for performance - each token type (identified by scope) is cached separately
 * and reused until it expires (with a 5-minute safety buffer).
 */
public class SmokeTestTokenProvider {

  private static final String TOKEN_URL =
      System.getenv()
          .getOrDefault("LAA_SMOKE_OAUTH_TOKEN_URL", "http://localhost:9998/entra/token");

  // Token expiry from mock server is 3600 seconds (1 hour)
  private static final long TOKEN_EXPIRY_MS = 3600_000L;

  // Safety buffer - refresh token 5 minutes before expiry
  private static final long EXPIRY_BUFFER_MS = 300_000L;

  // Cache: scope -> TokenCacheEntry
  private static final Map<String, TokenCacheEntry> tokenCache = new ConcurrentHashMap<>();

  /**
   * Obtain a token with the LAA_CASEWORKER role from the mock OAuth server.
   *
   * <p>Tokens are cached and reused until they expire (with a 5-minute safety buffer). This reduces
   * HTTP calls to the mock server during smoke test execution.
   *
   * @return a signed JWT with LAA_CASEWORKER role claims
   * @throws RuntimeException if token request fails
   */
  public static String getCaseworkerToken() {
    return getToken("api://laa-data-access-api/.default");
  }

  /**
   * Obtain a token from the mock OAuth server with the specified scope.
   *
   * <p>Tokens are cached by scope. If a valid cached token exists for the requested scope, it is
   * returned immediately. Otherwise, a new token is fetched via HTTP.
   *
   * @param scope the OAuth scope to request (e.g., "api://laa-data-access-api/.default")
   * @return a signed JWT
   * @throws RuntimeException if token request fails
   */
  private static String getToken(String scope) {
    long now = System.currentTimeMillis();

    // Check cache for valid token
    TokenCacheEntry cached = tokenCache.get(scope);
    if (cached != null && cached.expiryTime > now + EXPIRY_BUFFER_MS) {
      return cached.token;
    }

    // Fetch new token
    String token = fetchTokenFromServer(scope);

    // Cache the token
    tokenCache.put(scope, new TokenCacheEntry(token, now + TOKEN_EXPIRY_MS));

    return token;
  }

  /**
   * Fetch a new token from the mock OAuth server via HTTP POST.
   *
   * @param scope the OAuth scope to request
   * @return a signed JWT
   * @throws RuntimeException if token request fails
   */
  private static String fetchTokenFromServer(String scope) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "client_credentials");
    body.add("scope", scope);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

    try {
      RestTemplate restTemplate = new RestTemplate();
      ResponseEntity<Map<String, Object>> response =
          restTemplate.postForEntity(
              TOKEN_URL, request, (Class<Map<String, Object>>) (Class<?>) Map.class);

      if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
        throw new RuntimeException(
            "Mock OAuth server returned invalid response (missing access_token). "
                + "Server URL: "
                + TOKEN_URL);
      }

      return (String) response.getBody().get("access_token");

    } catch (RestClientException e) {
      throw new RuntimeException(
          "Failed to obtain token from mock OAuth server at "
              + TOKEN_URL
              + ". Is the mock server running? Check docker-compose.smoke-test.yml",
          e);
    }
  }

  /**
   * Clear the token cache. Useful for tests that need to force token regeneration or test token
   * expiry scenarios.
   */
  static void clearCache() {
    tokenCache.clear();
  }

  /** Immutable cache entry holding a token and its expiry time. */
  private static class TokenCacheEntry {
    final String token;
    final long expiryTime;

    TokenCacheEntry(String token, long expiryTime) {
      this.token = token;
      this.expiryTime = expiryTime;
    }
  }
}
