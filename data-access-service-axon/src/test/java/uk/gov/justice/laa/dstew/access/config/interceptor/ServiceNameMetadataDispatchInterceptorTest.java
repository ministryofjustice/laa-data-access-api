package uk.gov.justice.laa.dstew.access.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

class ServiceNameMetadataDispatchInterceptorTest {

  @Test
  void givenServiceNameContext_whenCommandIsDispatched_thenAddsServiceNameMetadata() {
    ServiceNameContext context = new ServiceNameContext();
    context.setServiceName(ServiceName.fromValue("CIVIL_APPLY"));
    ServiceNameMetadataDispatchInterceptor interceptor =
        new ServiceNameMetadataDispatchInterceptor(context);
    var command = GenericCommandMessage.asCommandMessage("command");

    var intercepted = interceptor.handle(List.of(command)).apply(0, command);

    assertThat(intercepted.getMetaData())
        .containsEntry(
            ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY,
            ServiceName.fromValue("CIVIL_APPLY"));
  }

  @Test
  void givenNoServiceNameContext_whenCommandIsDispatched_thenPreservesCommandMetadata() {
    ServiceNameMetadataDispatchInterceptor interceptor =
        new ServiceNameMetadataDispatchInterceptor(new ServiceNameContext());
    var command = GenericCommandMessage.asCommandMessage("command");

    var intercepted = interceptor.handle(List.of(command)).apply(0, command);

    assertThat(intercepted.getMetaData()).isEmpty();
  }
}
