package uk.gov.justice.laa.dstew.access.bdd.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;

/** Fetches named bearer tokens from the configured mock Entra server. */
public final class AuthTokenProvider {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    String allowedHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders", "");
    if (!allowedHeaders.toLowerCase().contains("host"))
      System.setProperty(
          "jdk.httpclient.allowRestrictedHeaders",
          allowedHeaders.isBlank() ? "host" : allowedHeaders + ",host");
  }

  private final BddConfiguration configuration;
  private final HttpClient httpClient;

  public AuthTokenProvider(BddConfiguration configuration) {
    this.configuration = configuration;
    httpClient = HttpClient.newHttpClient();
  }

  public AuthToken fetch(String tokenName) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(configuration.tokenUrl()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Host", configuration.tokenIssuerHost())
              .POST(HttpRequest.BodyPublishers.ofString(requestBody(tokenName)))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Mock Entra token request for \""
                + tokenName
                + "\" failed with HTTP "
                + response.statusCode()
                + ": "
                + response.body());
      }
      JsonNode accessToken = OBJECT_MAPPER.readTree(response.body()).path("access_token");
      if (accessToken.isMissingNode() || accessToken.asText().isBlank()) {
        throw new IllegalStateException(
            "Mock Entra token response for \"" + tokenName + "\" did not contain access_token");
      }
      return new AuthToken(accessToken.asText(), officeCode(accessToken.asText()));
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException) throw (IllegalStateException) exception;
      throw new IllegalStateException(
          "Could not fetch mock Entra token \"" + tokenName + "\"", exception);
    }
  }

  private String requestBody(String tokenName) {
    return "grant_type=client_credentials&client_id="
        + encode(tokenName)
        + "&client_secret="
        + encode(configuration.tokenClientSecret())
        + "&scope="
        + encode(configuration.tokenScope());
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String officeCode(String token) throws Exception {
    String[] sections = token.split("\\.");
    if (sections.length != 3) throw new IllegalStateException("Mock Entra returned an invalid JWT");
    JsonNode accounts =
        OBJECT_MAPPER
            .readTree(
                new String(Base64.getUrlDecoder().decode(sections[1]), StandardCharsets.UTF_8))
            .path("LAA_ACCOUNTS");
    return accounts.isArray() && !accounts.isEmpty() ? accounts.get(0).asText() : null;
  }

  public record AuthToken(String value, String officeCode) {}
}
