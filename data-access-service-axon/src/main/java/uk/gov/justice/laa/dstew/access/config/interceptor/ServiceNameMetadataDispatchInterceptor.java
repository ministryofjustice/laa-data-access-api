package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.time.Instant;
import java.util.Collections;
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
    public MessageStream<?> interceptOnDispatch(CommandMessage message,
                                                @Nullable ProcessingContext context,
                                                MessageDispatchInterceptorChain<CommandMessage> chain) {
      @Nullable String serviceName = String.valueOf(currentServiceName());

      if (serviceName == null) {
        return chain.proceed(message, context);
      }

      // Modify or enrich message
      CommandMessage enrichedMessage = message.andMetadata(Map.of(SERVICE_NAME_METADATA_KEY, serviceName));


      // Continue chain with modified message
      return chain.proceed(enrichedMessage, context);
    }



  private ServiceName currentServiceName() {
    try {
      return serviceNameContext.getServiceName();
    } catch (ScopeNotActiveException exception) {
      return null;
    }
  }
}
