package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Adds the request's service name to every command dispatched by this service. */
@Component
public class ServiceNameMetadataDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage<?>> {

  public static final String SERVICE_NAME_METADATA_KEY = "X-Service-Name";

  private final ServiceNameContext serviceNameContext;

  public ServiceNameMetadataDispatchInterceptor(ServiceNameContext serviceNameContext) {
    this.serviceNameContext = serviceNameContext;
  }

  @Override
  public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
      List<? extends CommandMessage<?>> messages) {
    ServiceName serviceName = currentServiceName();
    if (serviceName == null) {
      return (index, command) -> command;
    }
    return (index, command) -> command.andMetaData(Map.of(SERVICE_NAME_METADATA_KEY, serviceName));
  }

  private ServiceName currentServiceName() {
    try {
      return serviceNameContext.getServiceName();
    } catch (ScopeNotActiveException exception) {
      return null;
    }
  }
}
