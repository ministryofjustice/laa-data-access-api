package uk.gov.justice.laa.dstew.access.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;

/**
 * Converter for PriorAuthorityDocumentType enum. Handles conversion from request parameters
 * (lowercase values like "parental_responsibility") to Java enum constants (uppercase like
 * PARENTAL_RESPONSIBILITY).
 */
@Component
public class PriorAuthorityDocumentTypeConverter
    implements Converter<String, PriorAuthorityDocumentType> {

  @Override
  public PriorAuthorityDocumentType convert(String source) {
    if (source == null || source.isEmpty()) {
      return null;
    }
    return PriorAuthorityDocumentType.fromValue(source);
  }
}
