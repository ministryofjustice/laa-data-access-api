package uk.gov.justice.laa.dstew.access.config;

import static uk.gov.justice.laa.dstew.access.context.LoggingContext.CORRELATION_ID_HEADER;

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

/** Configuration class for RestClient bean. */
@ExcludeFromGeneratedCodeCoverage
@Configuration
@Slf4j
public class RestClientConfig {

  @Bean
  ClientHttpRequestInterceptor loggingInterceptor() {
    return new LoggingClientHttpRequestInterceptor();
  }

  @Bean
  ClientHttpRequestInterceptor correlationIdInterceptor() {
    return (request, body, execution) -> {
      String correlationId = LoggingContext.getCorrelationId();
      if (correlationId != null) {
        request.getHeaders().set(CORRELATION_ID_HEADER, correlationId);
      }
      return execution.execute(request, body);
    };
  }

  @Bean
  RestClient restClient(
      RestClient.Builder builder,
      ClientHttpRequestInterceptor loggingInterceptor,
      ClientHttpRequestInterceptor correlationIdInterceptor) {
    return builder
        .requestInterceptor(loggingInterceptor)
        .requestInterceptor(correlationIdInterceptor)
        .build();
  }

  @Bean
  RestClient sdsRestClient(
      RestClient.Builder builder,
      @Value("${app.sds-api.url}") String sdsApiUrl,
      ClientHttpRequestInterceptor loggingInterceptor,
      ClientHttpRequestInterceptor correlationIdInterceptor) {
    return builder
        .baseUrl(sdsApiUrl)
        .requestInterceptor(loggingInterceptor)
        .requestInterceptor(correlationIdInterceptor)
        .build();
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
      String correlationId = LoggingContext.getCorrelationId();
      String method = request.getMethod().name();
      String uri = request.getURI().toString();

      // Log outbound request
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbound request: method={}, uri={}, correlationId={}, headers={}",
            method,
            uri,
            correlationId,
            sanitizeHeaders(request));
      } else {
        log.info(
            "Outbound request: method={}, uri={}, correlationId={}", method, uri, correlationId);
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
              "Outbound response: method={}, uri={}, statusCode={}, duration={}ms, correlationId={}",
              method,
              uri,
              statusCode,
              duration,
              correlationId);
        } else if (statusCode >= 400 && statusCode < 500) {
          log.warn(
              "Outbound client error: method={}, uri={}, statusCode={}, duration={}ms, correlationId={}",
              method,
              uri,
              statusCode,
              duration,
              correlationId);
        } else if (statusCode >= 500) {
          log.error(
              "Outbound server error: method={}, uri={}, statusCode={}, duration={}ms, correlationId={}",
              method,
              uri,
              statusCode,
              duration,
              correlationId);
        }

        return response;

      } catch (IOException e) {
        long duration = System.currentTimeMillis() - startTime;
        log.error(
            "Outbound request failed: method={}, uri={}, duration={}ms, correlationId={}, error={}",
            method,
            uri,
            duration,
            correlationId,
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
