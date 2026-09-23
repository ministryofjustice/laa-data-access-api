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
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
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

  @SuppressWarnings("unchecked")
  private MessageDispatchInterceptorChain<CommandMessage> chain() {
    MessageDispatchInterceptorChain<CommandMessage> chain =
        mock(MessageDispatchInterceptorChain.class);
    when(chain.proceed(any(), isNull())).thenReturn(mock(MessageStream.class));
    return chain;
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
