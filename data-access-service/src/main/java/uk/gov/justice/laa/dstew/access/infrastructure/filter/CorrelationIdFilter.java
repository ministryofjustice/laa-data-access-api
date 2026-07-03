package uk.gov.justice.laa.dstew.access.infrastructure.filter;

import static uk.gov.justice.laa.dstew.access.context.LoggingContext.CORRELATION_ID_HEADER;

import com.fasterxml.uuid.Generators;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.context.LoggingContext;

/**
 * Servlet filter for correlation ID management using Micrometer baggage.
 *
 * <p>This filter generates UUID7 correlation IDs when not provided and uses Micrometer's baggage
 * mechanism for automatic propagation across services and MDC integration.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Generate UUID7 correlation ID if X-Correlation-ID header is missing
 *   <li>Add correlation ID to Micrometer baggage (auto-propagates to downstream services)
 *   <li>Return X-Correlation-ID in response headers
 *   <li>Set service context (name, environment) per LAA guardrails
 * </ul>
 *
 * <p>Micrometer baggage automatically:
 *
 * <ul>
 *   <li>Adds correlation ID to MDC for logging
 *   <li>Propagates correlation ID to downstream HTTP calls
 *   <li>Maintains correlation ID throughout the request lifecycle
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@ExcludeFromGeneratedCodeCoverage
public class CorrelationIdFilter extends OncePerRequestFilter {

  private final Tracer tracer;

  @Value("${spring.application.name:laa-data-access-api}")
  private String serviceName;

  @Value("${sentry.environment:local}")
  private String environment;

  public CorrelationIdFilter(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // Get correlation ID from header or generate UUID7
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);

      if (correlationId == null || correlationId.isBlank()) {
        // Generate UUID7 for guardrail compliance (time-ordered for better indexing)
        correlationId = Generators.timeBasedEpochGenerator().generate().toString();
      }

      // Add to Micrometer baggage - this automatically:
      // 1. Adds to MDC (via baggage.correlation.fields config)
      // 2. Propagates to downstream services (via baggage.remote-fields config)
      try (BaggageInScope correlationIdBaggage =
          tracer.createBaggageInScope("correlationId", correlationId)) {

        // Add correlation ID to response header for client traceability
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // Set additional service context in MDC (guardrail requirements)
        LoggingContext.setServiceName(serviceName);
        LoggingContext.setEnvironment(environment);
        LoggingContext.setRequestInfo(request.getMethod(), request.getRequestURI());

        // Log incoming request (guardrail requirement: log key actions)
        log.info(
            "Incoming request: method={}, uri={}, correlationId={}",
            request.getMethod(),
            request.getRequestURI(),
            correlationId);

        filterChain.doFilter(request, response);

        // Log response (guardrail requirement: log outcomes)
        LoggingContext.setStatusCode(response.getStatus());
        log.info(
            "Outgoing response: method={}, uri={}, statusCode={}, correlationId={}",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            correlationId);
      }
    } finally {
      // Clear custom MDC entries (Micrometer manages traceId/spanId/correlationId)
      LoggingContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Exclude actuator endpoints to avoid flooding logs
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator");
  }
}
