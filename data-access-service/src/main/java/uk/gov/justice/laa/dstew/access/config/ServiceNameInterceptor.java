package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/**
 * Interceptor to extract and store the X-Service-Name header from incoming requests.
 * The service name is stored in a request-scoped ServiceNameContext bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceNameInterceptor implements HandlerInterceptor {

  private static final String SERVICE_NAME_HEADER = "X-Service-Name";

  private final ServiceNameContext serviceNameContext;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String serviceNameHeader = request.getHeader(SERVICE_NAME_HEADER);

    if (serviceNameHeader != null) {
      try {
        ServiceName serviceName = ServiceName.fromValue(serviceNameHeader);
        serviceNameContext.setServiceName(serviceName);
        log.debug("Service name extracted from header: {}", serviceName);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid service name in header");
      }
    }

    return true;
  }
}
