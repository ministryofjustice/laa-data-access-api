package uk.gov.justice.laa.dstew.access.convertors;

import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Deserializer for MatterType enum.
 */
public class MatterTypeConvertor extends GenericEnumConvertor<MatterType> {
  public MatterTypeConvertor() {
    super(MatterType.class);
  }

}
