package uk.gov.justice.laa.dstew.access.applicationcontent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.exc.MismatchedInputException;
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
}
