package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

@ExtendWith(MockitoExtension.class)
class CreateApplicationDraftCommandHandlerTest {

  @Mock private ApplicationCreationDetailsFactory factory;
  @Mock private JsonSchemaValidator jsonSchemaValidator;
  @Mock private EventAppender eventAppender;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenNoApplicationYet_whenHandled_thenStartsDraftWithItsContent(boolean entityInjected) {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("provider", Map.of("officeCode", "1A001B"));
    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            "LAA-123",
            content,
            "{\"request\":true}",
            1,
            "schema",
            Instant.parse("2026-11-18T10:00:00Z"),
            List.of());
    ApplicationAggregate application = null;
    if (entityInjected) {
      application = mock(ApplicationAggregate.class);
      when(application.getApplicationId()).thenReturn(null);
    }

    UUID result =
        new CreateApplicationDraftCommandHandler()
            .handle(command, application, factory, jsonSchemaValidator, eventAppender);

    assertThat(result).isEqualTo(applicationId);
    verify(factory).validate(content);
    ArgumentCaptor<Object> appended = ArgumentCaptor.forClass(Object.class);
    verify(eventAppender).append(appended.capture());
    assertThat(appended.getValue())
        .isInstanceOfSatisfying(
            ApplicationDraftStartedEvent.class,
            event -> {
              assertThat(event.applicationId()).isEqualTo(applicationId);
              assertThat(event.laaReference()).isEqualTo("LAA-123");
              assertThat(event.officeCode()).isEqualTo("1A001B");
            });
  }
}
