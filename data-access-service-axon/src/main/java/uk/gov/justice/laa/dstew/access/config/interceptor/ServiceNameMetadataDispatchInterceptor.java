package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.util.Map;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Adds the request's service name to every command dispatched by this service. */
// TODO(axon4to5): migrate the body of this interceptor to the AF5 API — the signature has been
// rewritten but the body still references the AF4 `unitOfWork` / `interceptorChain` / `messages`
// parameters. Replace those with calls on `message`, `context`, `chain`. See
// docs/reference-guide/modules/migration/pages/paths/interceptors.adoc
@Component
public class ServiceNameMetadataDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage> {

  public static final String SERVICE_NAME_METADATA_KEY = "X-Service-Name";

  private final ServiceNameContext serviceNameContext;

  public ServiceNameMetadataDispatchInterceptor(ServiceNameContext serviceNameContext) {
    this.serviceNameContext = serviceNameContext;
  }

  @Override
  public MessageStream<?> interceptOnDispatch(
      CommandMessage message,
      ProcessingContext context,
      MessageDispatchInterceptorChain<CommandMessage> chain) {
    ServiceName serviceName = currentServiceName();
    if (serviceName == null) {
      return (index, command) -> command;
    }
    return (index, command) -> command.andMetadata(Map.of(SERVICE_NAME_METADATA_KEY, serviceName));
  }

  private ServiceName currentServiceName() {
    try {
      return serviceNameContext.getServiceName();
    } catch (ScopeNotActiveException exception) {
      return null;
    }
  }
}
