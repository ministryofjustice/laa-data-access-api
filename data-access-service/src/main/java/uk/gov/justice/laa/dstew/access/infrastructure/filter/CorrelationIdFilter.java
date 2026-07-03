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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.context.LoggingContext;

/** Servlet filter for correlation ID management using Micrometer baggage. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@ExcludeFromGeneratedCodeCoverage
public class CorrelationIdFilter extends OncePerRequestFilter {

  private final Tracer tracer;

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
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);

      if (correlationId == null || correlationId.isBlank()) {
        correlationId = Generators.timeBasedEpochGenerator().generate().toString();
      }

      try (BaggageInScope correlationIdBaggage =
          tracer.createBaggageInScope("correlationId", correlationId)) {

        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        log.atInfo()
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("url.path", request.getRequestURI())
            .log("Incoming request");

        filterChain.doFilter(request, response);

        log.atInfo()
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("url.path", request.getRequestURI())
            .addKeyValue("http.response.status_code", response.getStatus())
            .log("Outgoing response");
      }
    } finally {
      LoggingContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Exclude infrastructure endpoints to avoid flooding logs
    String uri = request.getRequestURI();
    if (uri == null) {
      return false;
    }
    return uri.startsWith("/actuator")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.startsWith("/api-docs");
  }
}
