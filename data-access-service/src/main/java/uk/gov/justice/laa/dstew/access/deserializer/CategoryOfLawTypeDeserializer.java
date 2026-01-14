package uk.gov.justice.laa.dstew.access.deserializer;

import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;

/**
 * Deserializer for CategoryOfLaw enum.
 */
public class CategoryOfLawTypeDeserializer extends GenericEnumDeserializer<CategoryOfLaw> {
  public CategoryOfLawTypeDeserializer() {
    super(CategoryOfLaw.class);
  }

}
