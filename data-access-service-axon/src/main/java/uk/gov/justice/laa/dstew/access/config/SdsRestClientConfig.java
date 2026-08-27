package uk.gov.justice.laa.dstew.access.config;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Configures the RestClient bean used for outbound SDS API calls. */
@Configuration
@ExcludeFromGeneratedCodeCoverage
public class SdsRestClientConfig {

  /**
   * Creates a RestClient for SDS API calls. Micrometer automatically propagates trace context and
   * baggage (including correlation ID).
   */
  @Bean("sdsRestClient")
  RestClient sdsRestClient(
      RestClient.Builder builder, @Value("${app.sds-api.url}") String sdsApiUrl) {
    return builder.baseUrl(sdsApiUrl).requestInterceptor(new SdsLoggingInterceptor()).build();
  }

  @Slf4j
  @ExcludeFromGeneratedCodeCoverage
  static class SdsLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<String> SENSITIVE_HEADERS =
        Set.of("authorization", "x-api-key", "cookie", "set-cookie", "x-auth-token");

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

      long startTime = System.currentTimeMillis();
      String method = request.getMethod().name();
      String uri = request.getURI().toString();

      if (log.isDebugEnabled()) {
        log.atDebug()
            .addKeyValue("http.request.method", method)
            .addKeyValue("url.full", uri)
            .addKeyValue("http.request.headers", sanitizeHeaders(request))
            .log("Outbound SDS request");
      } else {
        log.atInfo()
            .addKeyValue("http.request.method", method)
            .addKeyValue("url.full", uri)
            .log("Outbound SDS request");
      }

      try {
        ClientHttpResponse response = execution.execute(request, body);

        long duration = System.currentTimeMillis() - startTime;
        int statusCode = response.getStatusCode().value();

        if (statusCode >= 200 && statusCode < 300) {
          log.atInfo()
              .addKeyValue("http.request.method", method)
              .addKeyValue("url.full", uri)
              .addKeyValue("http.response.status_code", statusCode)
              .addKeyValue("event.duration", duration)
              .log("Outbound SDS response");
        } else if (statusCode >= 400 && statusCode < 500) {
          log.atWarn()
              .addKeyValue("http.request.method", method)
              .addKeyValue("url.full", uri)
              .addKeyValue("http.response.status_code", statusCode)
              .addKeyValue("event.duration", duration)
              .log("Outbound SDS client error");
        } else if (statusCode >= 500) {
          log.atError()
              .addKeyValue("http.request.method", method)
              .addKeyValue("url.full", uri)
              .addKeyValue("http.response.status_code", statusCode)
              .addKeyValue("event.duration", duration)
              .log("Outbound SDS server error");
        }

        return response;

      } catch (IOException e) {
        long duration = System.currentTimeMillis() - startTime;
        log.atError()
            .addKeyValue("http.request.method", method)
            .addKeyValue("url.full", uri)
            .addKeyValue("event.duration", duration)
            .addKeyValue("error.message", e.getMessage())
            .setCause(e)
            .log("Outbound SDS request failed");
        throw e;
      }
    }

    private Map<String, String> sanitizeHeaders(HttpRequest request) {
      return request.getHeaders().toSingleValueMap().entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  entry ->
                      SENSITIVE_HEADERS.contains(entry.getKey().toLowerCase())
                          ? "[REDACTED]"
                          : entry.getValue()));
    }
  }
}
