package uk.gov.justice.laa.dstew.access.mapper.deserializer;

import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;

/**
 * Deserializer for CategoryOfLaw enum.
 */
public class CategoryOfLawTypeDeserializer extends GenericEnumDeserializer<CategoryOfLaw> {
  public CategoryOfLawTypeDeserializer() {
    super(CategoryOfLaw.class);
  }


}
