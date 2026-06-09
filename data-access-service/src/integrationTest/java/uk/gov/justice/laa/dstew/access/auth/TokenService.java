package uk.gov.justice.laa.dstew.access.auth;

import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Fetches an OAuth2 {@code client_credentials} token from either:
 *
 * <ul>
 *   <li>A {@link MockOAuth2Container} instance (integration tests)
 *   <li>A live/deployed mock server URL read from env vars (infrastructure / smoke tests)
 * </ul>
 *
 * <h3>Integration test usage</h3>
 *
 * <pre>{@code
 * String token = TokenService.fromContainer(mockOAuth2Container).fetchToken();
 * }</pre>
 *
 * <h3>Infrastructure test usage</h3>
 *
 * <p>Set the following environment variables (or system properties) before running:
 *
 * <pre>
 * OAUTH_TOKEN_URL       e.g. http://mock-oauth2.my-env.svc:9999/entra/token
 * OAUTH_CLIENT_ID       defaults to "test"
 * OAUTH_CLIENT_SECRET   defaults to "test"
 * OAUTH_SCOPE           defaults to "api://laa-data-access-api/.default"
 * </pre>
 *
 * <pre>{@code
 * String token = TokenService.fromEnvironment().fetchToken();
 * }</pre>
 */
public class TokenService {

  private static final String DEFAULT_CLIENT_ID = "test";
  private static final String DEFAULT_CLIENT_SECRET = "test";
  private static final String DEFAULT_SCOPE = "api://laa-data-access-api/.default";

  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final String scope;

  private TokenService(String tokenUrl, String clientId, String clientSecret, String scope) {
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
  }

  /**
   * Creates a {@link TokenService} that talks to a Testcontainers-managed mock server.
   *
   * @param container a started {@link MockOAuth2Container}
   */
  public static TokenService fromContainer(
      uk.gov.justice.laa.dstew.access.containers.MockOAuth2Container container) {
    return new TokenService(
        container.getTokenUrl(), DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET, DEFAULT_SCOPE);
  }

  /**
   * Creates a {@link TokenService} that reads connection details from environment variables.
   *
   * <p>Required env var: {@code OAUTH_TOKEN_URL}.
   *
   * @throws IllegalStateException if {@code OAUTH_TOKEN_URL} is not set
   */
  public static TokenService fromEnvironment() {
    // Accept either OAUTH_TOKEN_URL (generic) or LAA_SMOKE_OAUTH_TOKEN_URL (smoke-test script
    // convention)
    String url = resolveEnv("OAUTH_TOKEN_URL", null);
    if (url == null || url.isBlank()) {
      url = resolveEnv("LAA_SMOKE_OAUTH_TOKEN_URL", null);
    }
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "OAUTH_TOKEN_URL (or LAA_SMOKE_OAUTH_TOKEN_URL) environment variable must be set for "
              + "infrastructure tests. Example: http://mock-oauth2.my-env.svc:9999/entra/token");
    }
    return new TokenService(
        url,
        resolveEnv("OAUTH_CLIENT_ID", DEFAULT_CLIENT_ID),
        resolveEnv("OAUTH_CLIENT_SECRET", DEFAULT_CLIENT_SECRET),
        resolveEnv("OAUTH_SCOPE", DEFAULT_SCOPE));
  }

  /**
   * Creates a {@link TokenService} pointing at the given token URL with default credentials. Useful
   * for tests that construct the URL themselves (e.g. from a {@code @DynamicPropertySource}).
   */
  public static TokenService forUrl(String tokenUrl) {
    return new TokenService(tokenUrl, DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET, DEFAULT_SCOPE);
  }

  /**
   * Performs a {@code client_credentials} token request and returns the raw JWT access token
   * string.
   *
   * @throws TokenFetchException if the request fails or the response contains no access_token
   */
  public String fetchToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("scope", scope);

    RestClient client = RestClient.create();

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          client
              .post()
              .uri(URI.create(tokenUrl))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);

      if (response == null || !response.containsKey("access_token")) {
        throw new TokenFetchException(
            "Token response from "
                + tokenUrl
                + " did not contain access_token. Response: "
                + response);
      }

      return (String) response.get("access_token");
    } catch (TokenFetchException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenFetchException(
          "Failed to fetch token from " + tokenUrl + ": " + e.getMessage(), e);
    }
  }

  /** Returns a Bearer token header value, e.g. {@code "Bearer eyJ..."}. */
  public String fetchBearerHeader() {
    return "Bearer " + fetchToken();
  }

  private static String resolveEnv(String key, String defaultValue) {
    String val = System.getenv(key);
    if (val == null || val.isBlank()) {
      val = System.getProperty(key);
    }
    return (val == null || val.isBlank()) ? defaultValue : val;
  }

  /** Thrown when a token cannot be obtained. */
  public static class TokenFetchException extends RuntimeException {
    public TokenFetchException(String message) {
      super(message);
    }

    public TokenFetchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
