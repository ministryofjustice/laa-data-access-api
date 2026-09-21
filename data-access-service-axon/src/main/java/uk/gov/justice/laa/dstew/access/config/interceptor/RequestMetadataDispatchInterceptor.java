package uk.gov.justice.laa.dstew.access.config.interceptor;

import java.util.HashMap;
import java.util.Map;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.security.SecurityHelper;

/** Adds the request's service name to every command dispatched by this service. */
@Component
@NullMarked
@ExcludeFromGeneratedCodeCoverage
public class RequestMetadataDispatchInterceptor
    implements MessageDispatchInterceptor<CommandMessage> {

  public static final String SERVICE_NAME_METADATA_KEY = "X-Service-Name";
  public static final String CORRELATION_ID_METADATA_KEY = "correlationId";
  public static final String AUTHENTICATED_USER_ID_KEY = "authenticatedUserId";

  private final ServiceNameContext serviceNameContext;

  public RequestMetadataDispatchInterceptor(ServiceNameContext serviceNameContext) {
    this.serviceNameContext = serviceNameContext;
  }

  @Override
  public MessageStream<?> interceptOnDispatch(
      CommandMessage message,
      @Nullable ProcessingContext context,
      MessageDispatchInterceptorChain<CommandMessage> chain) {
    Map<String, String> requestMetadata = currentRequestMetadata();
    if (requestMetadata.isEmpty()) {
      return chain.proceed(message, context);
    }
    CommandMessage enrichedMessage = message.andMetadata(requestMetadata);
    return chain.proceed(enrichedMessage, context);
  }

  private Map<String, String> currentRequestMetadata() {
    try {
      Map<String, String> metadata = new HashMap<>();
      ServiceName serviceName = serviceNameContext.getServiceName();
      if (serviceName != null) {
        metadata.put(SERVICE_NAME_METADATA_KEY, serviceName.getValue());
      }
      String correlationId = serviceNameContext.getCorrelationId();
      if (correlationId != null && !correlationId.isBlank()) {
        metadata.put(CORRELATION_ID_METADATA_KEY, correlationId);
      }

      String oid =
          SecurityHelper.getEntraOid()
              .orElseThrow(() -> new AccessDeniedException("An Entra OID is required"));

      metadata.put(AUTHENTICATED_USER_ID_KEY, oid);

      return Map.copyOf(metadata);
    } catch (ScopeNotActiveException _) {
      return Map.of();
    }
  }
}
