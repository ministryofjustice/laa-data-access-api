package uk.gov.justice.laa.dstew.access.convertors;

import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;

/**
 * Deserializer for CategoryOfLaw enum.
 */
public class CategoryOfLawTypeConvertor extends GenericEnumConvertor<CategoryOfLaw> {
  public CategoryOfLawTypeConvertor() {
    super(CategoryOfLaw.class);
  }

}
