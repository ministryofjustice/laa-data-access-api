package uk.gov.justice.laa.dstew.access.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
import uk.gov.justice.laa.dstew.access.context.LoggingContext;

/** Configuration class for RestClient bean with Micrometer Tracing support. */
@ExcludeFromGeneratedCodeCoverage
@Configuration
@Slf4j
public class RestClientConfig {

  @Bean
  ClientHttpRequestInterceptor loggingInterceptor() {
    return new LoggingClientHttpRequestInterceptor();
  }

  /**
   * Creates a RestClient with Micrometer observation support. Micrometer automatically propagates
   * trace context and baggage (including correlation ID).
   */
  @Bean
  RestClient restClient(
      RestClient.Builder builder, ClientHttpRequestInterceptor loggingInterceptor) {
    return builder.requestInterceptor(loggingInterceptor).build();
  }

  /**
   * Creates a RestClient for SDS API with Micrometer observation support. Micrometer automatically
   * propagates trace context and baggage (including correlation ID).
   */
  @Bean
  RestClient sdsRestClient(
      RestClient.Builder builder,
      @Value("${app.sds-api.url}") String sdsApiUrl,
      ClientHttpRequestInterceptor loggingInterceptor) {
    return builder.baseUrl(sdsApiUrl).requestInterceptor(loggingInterceptor).build();
  }

  /** Implementation of logging interceptor with structured logging support. */
  @Slf4j
  @ExcludeFromGeneratedCodeCoverage
  static class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<String> SENSITIVE_HEADERS =
        Set.of("authorization", "x-api-key", "cookie", "set-cookie", "x-auth-token");

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

      long startTime = System.currentTimeMillis();
      String traceId = LoggingContext.getCorrelationId();
      String method = request.getMethod().name();
      String uri = request.getURI().toString();

      // Log outbound request
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbound request: method={}, uri={}, traceId={}, headers={}",
            method,
            uri,
            traceId,
            sanitizeHeaders(request));
      } else {
        log.info("Outbound request: method={}, uri={}, traceId={}", method, uri, traceId);
      }

      ClientHttpResponse response;
      try {
        // Execute the request
        response = execution.execute(request, body);

        long duration = System.currentTimeMillis() - startTime;
        int statusCode = response.getStatusCode().value();

        // Log response based on status
        if (statusCode >= 200 && statusCode < 300) {
          log.info(
              "Outbound response: method={}, uri={}, statusCode={}, duration={}ms, traceId={}",
              method,
              uri,
              statusCode,
              duration,
              traceId);
        } else if (statusCode >= 400 && statusCode < 500) {
          log.warn(
              "Outbound client error: method={}, uri={}, statusCode={}, duration={}ms, traceId={}",
              method,
              uri,
              statusCode,
              duration,
              traceId);
        } else if (statusCode >= 500) {
          log.error(
              "Outbound server error: method={}, uri={}, statusCode={}, duration={}ms, traceId={}",
              method,
              uri,
              statusCode,
              duration,
              traceId);
        }

        return response;

      } catch (IOException e) {
        long duration = System.currentTimeMillis() - startTime;
        log.error(
            "Outbound request failed: method={}, uri={}, duration={}ms, traceId={}, error={}",
            method,
            uri,
            duration,
            traceId,
            e.getMessage(),
            e);
        throw e;
      }
    }

    /**
     * Sanitizes request headers by masking sensitive values.
     *
     * @param request the HTTP request
     * @return sanitized headers as string
     */
    private String sanitizeHeaders(HttpRequest request) {
      Map<String, List<String>> headers = new HashMap<>();
      request.getHeaders().forEach(headers::put);

      return headers.entrySet().stream()
          .map(entry -> formatHeader(entry.getKey(), entry.getValue()))
          .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Formats a single header entry, redacting sensitive values.
     *
     * @param key the header key
     * @param values the header values
     * @return formatted header string
     */
    private String formatHeader(String key, List<String> values) {
      String lowerKey = key.toLowerCase();
      boolean isSensitive = SENSITIVE_HEADERS.stream().anyMatch(lowerKey::contains);

      List<String> sanitizedValues = isSensitive ? List.of("[REDACTED]") : values;
      return key + "=" + sanitizedValues;
    }
  }
}
