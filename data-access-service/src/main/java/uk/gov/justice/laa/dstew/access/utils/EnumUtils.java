package uk.gov.justice.laa.dstew.access.utils;

import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

public class EnumUtils {

  public static CategoryOfLaw convertToCategoryOfLaw(final String value) {
    CategoryOfLawTypeConvertor categoryOfLawTypeConvertor = new CategoryOfLawTypeConvertor();
    return categoryOfLawTypeConvertor.lenientEnumConversion(value);
  }

  public static MatterType convertToMatterType(final String value) {
    MatterTypeConvertor matterTypeConvertor = new MatterTypeConvertor();
    return matterTypeConvertor.lenientEnumConversion(value);
  }
}
