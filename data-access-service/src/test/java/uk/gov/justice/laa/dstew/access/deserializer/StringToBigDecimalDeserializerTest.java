package uk.gov.justice.laa.dstew.access.deserializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;

class StringToBigDecimalDeserializerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final StringToBigDecimalDeserializer deserializer = new StringToBigDecimalDeserializer();

  record TestRecord(
      @JsonDeserialize(using = StringToBigDecimalDeserializer.class) BigDecimal value) {}

  @ParameterizedTest
  @ValueSource(strings = {"\"100\"", "\"2500.00\"", "\"0\"", "\"0.01\"", "\"-99.9\""})
  void givenValidNumericString_whenDeserialized_thenReturnsBigDecimal(String jsonValue) {
    String json = "{\"value\": " + jsonValue + "}";
    TestRecord result = mapper.readValue(json, TestRecord.class);
    assertThat(result.value()).isEqualTo(new BigDecimal(jsonValue.replace("\"", "")));
  }

  @Test
  void givenNullToken_whenDeserializeCalled_thenReturnsNull() {
    JsonParser parser = mock(JsonParser.class);
    DeserializationContext context = mock(DeserializationContext.class);
    when(parser.currentToken()).thenReturn(JsonToken.VALUE_NULL);

    BigDecimal result = deserializer.deserialize(parser, context);

    assertThat(result).isNull();
  }

  @Test
  void givenNullValue_whenDeserialized_thenReturnsNull() {
    String json = "{\"value\": null}";
    TestRecord result = mapper.readValue(json, TestRecord.class);
    assertThat(result.value()).isNull();
  }

  @Test
  void givenAbsentField_whenDeserialized_thenReturnsNull() {
    String json = "{}";
    TestRecord result = mapper.readValue(json, TestRecord.class);
    assertThat(result.value()).isNull();
  }

  @Test
  void givenInvalidString_whenDeserialized_thenThrowsMismatchedInputException() {
    String json = "{\"value\": \"not-a-number\"}";
    assertThatThrownBy(() -> mapper.readValue(json, TestRecord.class))
        .isInstanceOf(MismatchedInputException.class)
        .hasMessageContaining("Invalid value 'not-a-number'")
        .hasMessageContaining("expected a numeric string");
  }

  @Test
  void givenEmptyString_whenDeserialized_thenThrowsMismatchedInputException() {
    String json = "{\"value\": \"\"}";
    assertThatThrownBy(() -> mapper.readValue(json, TestRecord.class))
        .isInstanceOf(MismatchedInputException.class)
        .hasMessageContaining("expected a numeric string");
  }

  @Test
  void givenNullValue_getNullValue_returnsNull() {
    assertThat(deserializer.getNullValue(null)).isNull();
  }
}
