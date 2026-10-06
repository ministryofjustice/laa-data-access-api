package uk.gov.justice.laa.dstew.access.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

class RequestMetadataDispatchInterceptorTest {

  @BeforeEach
  void setUp() {
    Jwt jwt = jwt().claim("oid", "entra-object-id").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenServiceNameContextAndEntraOid_whenCommandIsDispatched_thenAddsServiceNameMetadata() {
    ServiceNameContext context = new ServiceNameContext();
    context.setServiceName(ServiceName.fromValue("CIVIL_APPLY"));
    context.setCorrelationId("corr-2096");

    RequestMetadataDispatchInterceptor interceptor =
        new RequestMetadataDispatchInterceptor(context);
    var command = new GenericCommandMessage(new MessageType(String.class), "command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(command, null, chain);

    ArgumentCaptor<CommandMessage> intercepted = ArgumentCaptor.forClass(CommandMessage.class);
    verify(chain).proceed(intercepted.capture(), isNull());
    assertThat(intercepted.getValue().metadata())
        .containsEntry(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY")
        .containsEntry(RequestMetadataDispatchInterceptor.CORRELATION_ID_METADATA_KEY, "corr-2096")
        .containsEntry(
            RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY, "entra-object-id");
  }

  @Test
  void
      givenNoServiceNameContext_whenCommandIsDispatched_thenOnlyAuthenticatedUserMetadataIsAdded() {
    RequestMetadataDispatchInterceptor interceptor =
        new RequestMetadataDispatchInterceptor(new ServiceNameContext());
    var command = new GenericCommandMessage(new MessageType(String.class), "command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(command, null, chain);

    ArgumentCaptor<CommandMessage> intercepted = ArgumentCaptor.forClass(CommandMessage.class);
    verify(chain).proceed(intercepted.capture(), isNull());
    assertThat(intercepted.getValue().metadata())
        .containsEntry(
            RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY, "entra-object-id");
  }

  @Test
  void givenNoSecurityContext_whenCommandIsDispatched_thenThrowsException() {
    RequestMetadataDispatchInterceptor interceptor =
        new RequestMetadataDispatchInterceptor(new ServiceNameContext());
    var command = new GenericCommandMessage(new MessageType(String.class), "command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    SecurityContextHolder.clearContext();

    verifyNoInteractions(chain);
    assertThatThrownBy(() -> interceptor.interceptOnDispatch(command, null, chain))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("An Entra OID is required");
  }

  @Test
  void givenServiceNameMetadata_whenRead_thenReturnsServiceName() {
    EventMessage event =
        eventMessage(
            Map.of(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY"));

    assertThat(RequestMetadataDispatchInterceptor.serviceName(event)).isEqualTo("CIVIL_APPLY");
  }

  @Test
  void givenNoServiceNameMetadata_whenRead_thenReturnsNull() {
    EventMessage event = eventMessage(Map.of());

    assertThat(RequestMetadataDispatchInterceptor.serviceName(event)).isNull();
  }

  @Test
  void givenAuthenticatedUserMetadata_whenRead_thenReturnsCaseworkerId() {
    UUID caseworkerId = UUID.randomUUID();
    EventMessage event =
        eventMessage(
            Map.of(
                RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY,
                caseworkerId.toString()));

    assertThat(RequestMetadataDispatchInterceptor.caseworkerId(event)).isEqualTo(caseworkerId);
  }

  @Test
  void givenNoAuthenticatedUserMetadata_whenRead_thenReturnsNull() {
    EventMessage event = eventMessage(Map.of());

    assertThat(RequestMetadataDispatchInterceptor.caseworkerId(event)).isNull();
  }

  @SuppressWarnings("unchecked")
  private MessageDispatchInterceptorChain<CommandMessage> chain() {
    MessageDispatchInterceptorChain<CommandMessage> chain =
        mock(MessageDispatchInterceptorChain.class);
    when(chain.proceed(any(), isNull())).thenReturn(mock(MessageStream.class));
    return chain;
  }

  private EventMessage eventMessage(Map<String, String> metadata) {
    return new GenericEventMessage(
        "event-id",
        new MessageType(String.class),
        "event",
        metadata,
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private Jwt.Builder jwt() {
    Instant issuedAt = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user")
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plusSeconds(300));
  }
}
