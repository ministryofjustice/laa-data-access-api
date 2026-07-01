package uk.gov.justice.laa.dstew.access.infrastructure.filter;

import static uk.gov.justice.laa.dstew.access.context.LoggingContext.CORRELATION_ID_HEADER;

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
import uk.gov.justice.laa.dstew.access.context.LoggingContext;

/** Servlet filter that manages correlation IDs for request tracing. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Value("${spring.application.name:laa-data-access-api}")
  private String serviceName;

  @Value("${sentry.environment:local}")
  private String environment;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // Extract or generate correlation ID
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);
      correlationId = LoggingContext.setCorrelationId(correlationId);

      // Add correlation ID to response headers for traceability
      response.setHeader(CORRELATION_ID_HEADER, correlationId);

      // Set additional context
      LoggingContext.setServiceName(serviceName);
      LoggingContext.setEnvironment(environment);
      LoggingContext.setRequestInfo(request.getMethod(), request.getRequestURI());

      // Log the incoming request with structured context
      log.info(
          "Incoming request: method={}, uri={}, correlationId={}",
          request.getMethod(),
          request.getRequestURI(),
          correlationId);

      filterChain.doFilter(request, response);

      // Log response after processing
      LoggingContext.setStatusCode(response.getStatus());
      log.info(
          "Outgoing response: method={}, uri={}, statusCode={}, correlationId={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          correlationId);

    } finally {
      // Always clear MDC to prevent context leaking to other requests in thread pools
      LoggingContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Exclude actuator endpoints to avoid flooding logs with health checks and metrics
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/actuator");
  }
}
