package uk.gov.justice.laa.dstew.access.applicationcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;

class JacksonExceptionMessageBuilderTest {

  @Test
  void givenNonMismatchedInputException_whenBuildMessage_thenReturnsOriginalMessage() {
    JacksonException ex = new JacksonException("original message") {};

    String result = JacksonExceptionMessageBuilder.buildMessage(ex);

    assertThat(result).isEqualTo("original message");
  }

  @Test
  void givenMismatchedInputWithEnumTargetType_whenBuildMessage_thenEnumConstantsListed() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, AutoGrantedState.class, "test");

    String result = JacksonExceptionMessageBuilder.buildMessage(mie);

    assertThat(result).contains("PENDING").contains("AUTOGRANTED").contains("MANUAL");
  }

  @Test
  void givenEmptyPath_whenBuildFieldPath_thenReturnsUnknown() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, String.class, "test");

    String result = JacksonExceptionMessageBuilder.buildFieldPath(mie);

    assertThat(result).isEqualTo("unknown");
  }

  @Test
  void givenMismatchedInputWithNullTargetType_whenBuildMessage_thenUsesObjectClass() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, (Class<?>) null, "test");

    String result = JacksonExceptionMessageBuilder.buildMessage(mie);

    assertThat(result).contains("Object");
  }

  @Test
  void givenNullEnumClass_whenBuildMessageForInvalidEnum_thenReturnsIaeMessage() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, (Class<?>) null, "test");
    IllegalArgumentException iae = new IllegalArgumentException("invalid enum");

    String result = JacksonExceptionMessageBuilder.buildMessageForInvalidEnum(iae, mie);

    assertThat(result).isEqualTo("invalid enum");
  }

  @Test
  void givenEnumClass_whenBuildMessageForInvalidEnum_thenIncludesValidValues() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, AutoGrantedState.class, "test");
    IllegalArgumentException iae = new IllegalArgumentException("invalid enum");

    assertThat(JacksonExceptionMessageBuilder.buildMessageForInvalidEnum(iae, mie))
        .isEqualTo("invalid enum. Valid values are: PENDING, AUTOGRANTED, MANUAL");
  }

  @Test
  void givenNonEnumClass_whenBuildMessageForInvalidEnum_thenReturnsIaeMessage() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, String.class, "test");
    IllegalArgumentException iae = new IllegalArgumentException("invalid enum");

    assertThat(JacksonExceptionMessageBuilder.buildMessageForInvalidEnum(iae, mie))
        .isEqualTo("invalid enum");
  }

  @Test
  void givenIndexedPath_whenBuildFieldPath_thenIncludesArrayIndex() {
    MismatchedInputException mie =
        MismatchedInputException.from((JsonParser) null, String.class, "test");
    mie.prependPath(String.class, 0);
    mie.prependPath(String.class, "proceedings");

    assertThat(JacksonExceptionMessageBuilder.buildFieldPath(mie)).isEqualTo("proceedings[0]");
  }

  @Test
  void givenUnrecognisedProperty_whenBuildMessage_thenIncludesPropertyAndPath() {
    UnrecognizedPropertyException exception =
        UnrecognizedPropertyException.from(
            mock(JsonParser.class), AutoGrantedState.class, "emergencyLevelOfService", List.of());
    exception.prependPath(AutoGrantedState.class, "proceedings");

    assertThat(JacksonExceptionMessageBuilder.buildMessage(exception))
        .isEqualTo(
            "Unknown JSON property 'emergencyLevelOfService' at "
                + "'proceedings.emergencyLevelOfService'.");
    assertThat(JacksonExceptionMessageBuilder.unrecognisedPropertyName(exception))
        .isEqualTo("emergencyLevelOfService");
  }

  @Test
  void givenWrappedJacksonException_whenFindingJacksonException_thenReturnsNestedException() {
    JacksonException exception = new JacksonException("original message") {};

    assertThat(
            JacksonExceptionMessageBuilder.findJacksonException(
                new IllegalStateException(new RuntimeException(exception))))
        .containsSame(exception);
  }

  @Test
  void givenCauseChainWithoutJacksonException_whenFindingJacksonException_thenReturnsEmpty() {
    assertThat(
            JacksonExceptionMessageBuilder.findJacksonException(
                new IllegalStateException(new RuntimeException("not Jackson"))))
        .isEmpty();
  }

  @Test
  void givenNonJacksonThrowable_whenBuildMessage_thenReturnsThrowableMessage() {
    assertThat(
            JacksonExceptionMessageBuilder.buildMessage(
                new IllegalArgumentException("not Jackson")))
        .isEqualTo("not Jackson");
  }

  @Test
  void givenWrappedJacksonTwoUnknownProperty_whenBuildingMessage_thenIncludesPropertyAndPath() {
    com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException exception =
        com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.from(
            mock(com.fasterxml.jackson.core.JsonParser.class),
            AutoGrantedState.class,
            "emergencyLevelOfService",
            List.of());
    exception.prependPath(AutoGrantedState.class, "proceedings");

    assertThat(
            JacksonExceptionMessageBuilder.findJacksonException(
                new IllegalArgumentException("Hibernate JSON mapping failed", exception)))
        .containsSame(exception);
    assertThat(JacksonExceptionMessageBuilder.buildMessage(exception))
        .isEqualTo(
            "Unknown JSON property 'emergencyLevelOfService' at "
                + "'proceedings.emergencyLevelOfService'.");
  }

  @Test
  void
      givenJacksonTwoMismatchedInputWithNonEnumTarget_whenBuildingMessage_thenIncludesTypeAndIndexedPath() {
    com.fasterxml.jackson.databind.exc.MismatchedInputException exception =
        com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
            (com.fasterxml.jackson.core.JsonParser) null, String.class, "test");
    exception.prependPath(String.class, 0);
    exception.prependPath(String.class, "proceedings");

    assertThat(JacksonExceptionMessageBuilder.buildMessage(exception))
        .isEqualTo("Invalid data type for field 'proceedings[0]'. Expected: String.");
  }

  @Test
  void givenJacksonTwoMismatchedInputWithEnumTarget_whenBuildingMessage_thenListsEnumValues() {
    com.fasterxml.jackson.databind.exc.MismatchedInputException exception =
        com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
            (com.fasterxml.jackson.core.JsonParser) null, AutoGrantedState.class, "test");

    assertThat(JacksonExceptionMessageBuilder.buildMessage(exception))
        .isEqualTo(
            "Invalid data type for field 'unknown'. Expected: [PENDING, AUTOGRANTED, MANUAL].");
  }
}
