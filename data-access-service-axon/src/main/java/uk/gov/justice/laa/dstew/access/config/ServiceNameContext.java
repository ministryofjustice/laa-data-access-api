package uk.gov.justice.laa.dstew.access.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Stores the service name supplied by the current HTTP request. */
@Component
@RequestScope
public class ServiceNameContext {

  private ServiceName serviceName;

  public void setServiceName(ServiceName serviceName) {
    this.serviceName = serviceName;
  }

  public ServiceName getServiceName() {
    return serviceName;
  }
}
