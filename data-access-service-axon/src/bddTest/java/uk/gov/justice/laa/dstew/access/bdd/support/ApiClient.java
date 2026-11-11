package uk.gov.justice.laa.dstew.access.bdd.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;

public final class ApiClient implements AutoCloseable {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final BddConfiguration configuration;
  private final Playwright playwright;
  private APIRequestContext request;

  public ApiClient(BddConfiguration configuration) {
    this.configuration = configuration;
    playwright = Playwright.create();
  }

  public void useBearerToken(String token) {
    if (request != null) request.dispose();
    request =
        playwright
            .request()
            .newContext(
                new APIRequest.NewContextOptions()
                    .setBaseURL(configuration.baseUrl())
                    .setExtraHTTPHeaders(
                        Map.of(
                            "Authorization",
                            "Bearer " + token,
                            "X-Service-Name",
                            configuration.serviceName(),
                            "X-Schema-Version",
                            configuration.schemaVersion())));
  }

  public ApiResponse get(String path) {
    return response(request.get(path));
  }

  public ApiResponse post(String path) {
    return response(request.post(path));
  }

  public ApiResponse post(String path, Object body) {
    return response(request.post(path, RequestOptions.create().setData(body)));
  }

  public ApiResponse patch(String path, Object body) {
    return response(request.patch(path, RequestOptions.create().setData(body)));
  }

  @Override
  public void close() {
    if (request != null) request.dispose();
    playwright.close();
  }

  private ApiResponse response(APIResponse response) {
    try {
      String text = response.text();
      JsonNode body =
          text.isBlank() ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(text);
      return new ApiResponse(response.status(), response.headers(), body);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Could not parse HTTP response " + response.status(), exception);
    }
  }
}
