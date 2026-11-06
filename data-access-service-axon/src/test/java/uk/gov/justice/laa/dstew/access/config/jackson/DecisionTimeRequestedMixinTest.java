package uk.gov.justice.laa.dstew.access.config.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.DecisionTimeRequested;
import uk.gov.justice.laa.dstew.access.model.SubmitTimeRequested;

@ExtendWith(MockitoExtension.class)
class DecisionTimeRequestedMixinTest {

  @Test
  void givenNumericStringHours_whenSubmitTimeRequestedDeserialised_thenCoercesHours() {
    var timeRequested =
        mapper.readValue("{\"hours\":\"2\",\"minutes\":0}", SubmitTimeRequested.class);

    assertThat(timeRequested.getHours()).isEqualTo(2);
  }

  @Mock private JsonParser unexpectedParser;

  @Mock private DeserializationContext context;

  private final JsonMapper mapper =
      JsonMapper.builder()
          .addMixIn(DecisionTimeRequested.class, DecisionTimeRequestedMixin.class)
          .build();

  @Test
  void givenIntegerValues_whenDeserialised_thenReturnsHoursAndMinutes() {
    var timeRequested =
        mapper.readValue("{\"hours\":3,\"minutes\":15}", DecisionTimeRequested.class);

    assertThat(timeRequested.getHours()).isEqualTo(3);
    assertThat(timeRequested.getMinutes()).isEqualTo(15);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2.5", "\"2\"", "\"abc\"", "true", "[]"})
  void givenNonIntegerHours_whenDeserialised_thenThrowsMismatchedInput(String hours) {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"hours\":%s,\"minutes\":0}".formatted(hours), DecisionTimeRequested.class))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void givenNonIntegerMinutes_whenDeserialised_thenThrowsMismatchedInput() {
    assertThatThrownBy(
            () -> mapper.readValue("{\"hours\":1,\"minutes\":1.5}", DecisionTimeRequested.class))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void givenNullHours_whenDeserialised_thenHoursIsNull() {
    var timeRequested =
        mapper.readValue("{\"hours\":null,\"minutes\":0}", DecisionTimeRequested.class);

    assertThat(timeRequested.getHours()).isNull();
  }

  @Test
  void givenUnexpectedToken_whenDeserialised_thenDelegatesToContext() {
    when(unexpectedParser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
    when(context.handleUnexpectedToken(Integer.class, unexpectedParser)).thenReturn(null);

    var result = new StrictIntegerDeserializer().deserialize(unexpectedParser, context);

    assertThat(result).isNull();
    verify(context).handleUnexpectedToken(Integer.class, unexpectedParser);
  }
}
