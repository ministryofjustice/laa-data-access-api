package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.util.Map;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** Adds the request's service name to every command dispatched by this service. */
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
      @Nullable ProcessingContext context,
      MessageDispatchInterceptorChain<CommandMessage> chain) {
    ServiceName serviceName = currentServiceName();
    if (serviceName == null) {
      return chain.proceed(message, context);
    }
    CommandMessage enrichedMessage =
        message.andMetadata(Map.of(SERVICE_NAME_METADATA_KEY, serviceName.getValue()));
    return chain.proceed(enrichedMessage, context);
  }

  @Nullable
  private ServiceName currentServiceName() {
    try {
      return serviceNameContext.getServiceName();
    } catch (ScopeNotActiveException exception) {
      return null;
    }
  }
}
