package uk.gov.justice.laa.dstew.access.config.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Captures the X-Service-Name HTTP header for the duration of an API request. */
@Component
@ExcludeFromGeneratedCodeCoverage
public class ServiceNameInterceptor implements HandlerInterceptor {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private final ServiceNameContext serviceNameContext;

  public ServiceNameInterceptor(ServiceNameContext serviceNameContext) {
    this.serviceNameContext = serviceNameContext;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
    String serviceNameHeader = request.getHeader("X-Service-Name");
    if (serviceNameHeader != null) {
      serviceNameContext.setServiceName(ServiceName.fromValue(serviceNameHeader));
    }
    String correlationId = request.getHeader(CORRELATION_ID_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    serviceNameContext.setCorrelationId(correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);
    return true;
  }
}
