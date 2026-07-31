package uk.gov.justice.laa.dstew.access.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

class ServiceNameMetadataDispatchInterceptorTest {

  @Test
  void givenServiceNameContext_whenCommandIsDispatched_thenAddsServiceNameMetadata() {
    ServiceNameContext context = new ServiceNameContext();
    context.setServiceName(ServiceName.fromValue("CIVIL_APPLY"));
    ServiceNameMetadataDispatchInterceptor interceptor =
        new ServiceNameMetadataDispatchInterceptor(context);
    var command = new GenericCommandMessage(new MessageType(String.class), "command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(command, null, chain);

    ArgumentCaptor<CommandMessage> intercepted = ArgumentCaptor.forClass(CommandMessage.class);
    verify(chain).proceed(intercepted.capture(), isNull());
    assertThat(intercepted.getValue().metadata())
        .containsEntry(
            ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY");
  }

  @Test
  void givenNoServiceNameContext_whenCommandIsDispatched_thenPreservesCommandMetadata() {
    ServiceNameMetadataDispatchInterceptor interceptor =
        new ServiceNameMetadataDispatchInterceptor(new ServiceNameContext());
    var command = new GenericCommandMessage(new MessageType(String.class), "command");
    MessageDispatchInterceptorChain<CommandMessage> chain = chain();

    interceptor.interceptOnDispatch(command, null, chain);

    ArgumentCaptor<CommandMessage> intercepted = ArgumentCaptor.forClass(CommandMessage.class);
    verify(chain).proceed(intercepted.capture(), isNull());
    assertThat(intercepted.getValue().metadata()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private MessageDispatchInterceptorChain<CommandMessage> chain() {
    MessageDispatchInterceptorChain<CommandMessage> chain =
        mock(MessageDispatchInterceptorChain.class);
    when(chain.proceed(any(), isNull())).thenReturn(mock(MessageStream.class));
    return chain;
  }
}
