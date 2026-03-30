package uk.gov.justice.laa.dstew.access.config.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Interceptor for measuring request processing time.
 *
 * <p>Records the end-to-end processing time of each HTTP request
 * and exposes it as a Micrometer Timer metric for Prometheus monitoring.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsInterceptor implements HandlerInterceptor {

  private static final String START_TIME_ATTRIBUTE = "metricsStartTime";
  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /**
   * Captures the start time of the request processing and stores it in the request attributes.
   *
   * @param request  the incoming HTTP request
   * @param response the HTTP response
   * @param handler  the handler for the request
   * @return defaults to true
   */
  @Override
  public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,
                           @NonNull Object handler) {
    request.setAttribute(START_TIME_ATTRIBUTE, Instant.now().toEpochMilli());
    return true;
  }

  /**
   * Calculates the duration of the request processing and records it in a Micrometer Timer metric.
   *
   * @param request  http  request
   * @param response http response
   * @param handler  the handler for the request
   * @param ex       exception thrown during request processing, if any
   */
  @Override
  public void afterCompletion(HttpServletRequest request, @NonNull HttpServletResponse response,
                              @NonNull Object handler, Exception ex) {
    Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);

    if (startTime != null) {
      long duration = Instant.now().toEpochMilli() - startTime;

      String uri = getRouteTemplate(request);
      String method = request.getMethod();
      String status = String.valueOf(response.getStatus());
      String cacheKey = method + ":" + uri + ":" + status;

      timerCache.computeIfAbsent(cacheKey, key ->
          Timer.builder("endpoint.processing.time")
              .description("Endpoint request processing time")
              .tag("uri", uri)
              .tag("method", method)
              .tag("status", status)
              .publishPercentiles(0.05, 0.5, 0.95, 0.99)
              .register(meterRegistry)
      ).record(java.time.Duration.ofMillis(duration));

      if (log.isDebugEnabled()) {
        log.debug("Request processed: method={}, path={}, status={}, duration={}ms",
            method, uri, response.getStatus(), duration);
      }
    }
  }

  /**
   * Returns the matched route template (e.g. {@code /api/v0/applications/{id}}) as resolved by
   * Spring MVC, falling back to a normalized raw URI for unmatched requests (e.g. 404s).
   *
   * <p>Using the route template prevents high-cardinality {@code uri} tags caused by UUID or
   * numeric path variables appearing verbatim in the metric.</p>
   *
   * @param request the incoming HTTP request
   * @return the route template, or a normalized URI if no template is available
   */
  private String getRouteTemplate(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern != null) {
      return pattern.toString();
    }
    return normalizeUri(request.getRequestURI());
  }

  /**
   * Fallback URI normalization for requests that did not match a handler (e.g. 404s).
   *
   * <p>Replaces UUID and numeric path segments with {@code {id}} to avoid cardinality explosion
   * even when no route template is available.</p>
   *
   * @param uri the raw request URI
   * @return normalized URI
   */
  private String normalizeUri(String uri) {
    if (uri.startsWith("/api/")) {
      // Replace UUID path segments
      uri = uri.replaceAll(
          "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)",
          "/{id}");
      // Replace remaining numeric path segments
      uri = uri.replaceAll("/\\d+(?=/|$)", "/{id}");
    }
    return uri;
  }
}
