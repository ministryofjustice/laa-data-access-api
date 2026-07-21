package uk.gov.justice.laa.dstew.access.config.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Captures the X-Service-Name HTTP header for the duration of an API request. */
@Component
public class ServiceNameInterceptor implements HandlerInterceptor {

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
    return true;
  }
}
