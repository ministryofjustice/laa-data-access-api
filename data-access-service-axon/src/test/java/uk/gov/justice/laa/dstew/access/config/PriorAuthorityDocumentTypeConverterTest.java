package uk.gov.justice.laa.dstew.access.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;

class PriorAuthorityDocumentTypeConverterTest {

  private final PriorAuthorityDocumentTypeConverter converter =
      new PriorAuthorityDocumentTypeConverter();

  @Test
  void shouldConvertLowercaseValueToEnum() {
    PriorAuthorityDocumentType result = converter.convert("parental_responsibility");
    assertEquals(PriorAuthorityDocumentType.PARENTAL_RESPONSIBILITY, result);
  }

  @Test
  void shouldConvertAnyValidEnumValue() {
    assertEquals(PriorAuthorityDocumentType.MERITS_REPORT, converter.convert("merits_report"));
    assertEquals(
        PriorAuthorityDocumentType.STATEMENT_OF_CASE, converter.convert("statement_of_case"));
    assertEquals(PriorAuthorityDocumentType.EXPERT_REPORT, converter.convert("expert_report"));
  }

  @Test
  void shouldHandleNullInput() {
    assertNull(converter.convert(null));
  }

  @Test
  void shouldHandleEmptyString() {
    assertNull(converter.convert(""));
  }

  @Test
  void shouldThrowOnInvalidValue() {
    assertThrows(IllegalArgumentException.class, () -> converter.convert("invalid_value"));
  }
}
