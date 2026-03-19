package uk.gov.justice.laa.dstew.access.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/**
 * Request-scoped context holder for storing the service name from the request header.
 * This is populated by the ServiceNameInterceptor and can be injected into services.
 * Being request-scoped, each HTTP request gets its own instance, avoiding ThreadLocal complexity.
 */
@Component
@RequestScope
public class ServiceNameContext {

  private ServiceName serviceName;

  /**
   * Sets the service name for the current request.
   *
   * @param serviceName the service name to store
   */
  public void setServiceName(ServiceName serviceName) {
    this.serviceName = serviceName;
  }

  /**
   * Gets the service name for the current request.
   *
   * @return the stored service name, or null if not set
   */
  public ServiceName getServiceName() {
    return this.serviceName;
  }
}
