package uk.gov.justice.laa.dstew.access.applicationcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

class StringToBigDecimalDeserializerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  record TestRecord(
      @JsonDeserialize(using = StringToBigDecimalDeserializer.class) BigDecimal value) {}

  @ParameterizedTest
  @ValueSource(strings = {"\"100\"", "\"2500.00\"", "\"0\"", "\"0.01\""})
  void givenValidNumericString_whenDeserialized_thenReturnsBigDecimal(String jsonValue)
      throws Exception {
    String json = "{\"value\": " + jsonValue + "}";
    TestRecord result = mapper.readValue(json, TestRecord.class);
    assertThat(result.value()).isEqualTo(new BigDecimal(jsonValue.replace("\"", "")));
  }

  @Test
  void givenNullValue_whenDeserialized_thenReturnsNull() throws Exception {
    String json = "{\"value\": null}";
    TestRecord result = mapper.readValue(json, TestRecord.class);
    assertThat(result.value()).isNull();
  }

  @Test
  void givenInvalidString_whenDeserialized_thenThrowsException() {
    String json = "{\"value\": \"not-a-number\"}";
    assertThatThrownBy(() -> mapper.readValue(json, TestRecord.class))
        .hasMessageContaining("BigDecimal");
  }

  @Test
  void givenEmptyString_whenDeserialized_thenThrowsException() {
    String json = "{\"value\": \"\"}";
    assertThatThrownBy(() -> mapper.readValue(json, TestRecord.class))
        .hasMessageContaining("BigDecimal");
  }
}
