package uk.gov.justice.laa.dstew.access.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Stores the service name supplied by the current HTTP request. */
@Component
@RequestScope
@ExcludeFromGeneratedCodeCoverage
public class ServiceNameContext {

  private ServiceName serviceName;
  private String correlationId;

  public void setServiceName(ServiceName serviceName) {
    this.serviceName = serviceName;
  }

  public ServiceName getServiceName() {
    return serviceName;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
