package uk.gov.justice.laa.dstew.access.utils;

import uk.gov.justice.laa.dstew.access.convertors.CategoryOfLawTypeConvertor;
import uk.gov.justice.laa.dstew.access.convertors.MatterTypeConvertor;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Utility class for parsing string values into enum types. This class provides methods to convert
 * string representations of enums into their corresponding enum values. It uses lenient conversion,
 * allowing for case-insensitive matching and handling of null or empty.
 */
public class EnumParsingUtils {

  /**
   * Converts to enum with lenient parsing, allowing for case-insensitive matching and handling of
   * null or empty values.
   *
   * @param value string of category of law to be converted to enum
   * @return CategoryOfLaw enum value corresponding to the input string, or null if the input is
   *     null or empty
   * @throws IllegalArgumentException if the input string does not match any enum value
   */
  public static CategoryOfLaw convertToCategoryOfLaw(final String value) {
    CategoryOfLawTypeConvertor categoryOfLawTypeConvertor = new CategoryOfLawTypeConvertor();
    return categoryOfLawTypeConvertor.lenientEnumConversion(value);
  }

  /**
   * Converts to enum with lenient parsing, allowing for case-insensitive matching and handling of
   * null or empty values.
   *
   * @param value string of matter type to be converted to enum
   * @return MatterType enum value corresponding to the input string, or null if the input is null
   *     or empty
   * @throws IllegalArgumentException if the input string does not match any enum value
   */
  public static MatterType convertToMatterType(final String value) {
    MatterTypeConvertor matterTypeConvertor = new MatterTypeConvertor();
    return matterTypeConvertor.lenientEnumConversion(value);
  }
}
