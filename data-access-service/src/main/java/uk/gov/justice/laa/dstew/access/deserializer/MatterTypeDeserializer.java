package uk.gov.justice.laa.dstew.access.deserializer;

import uk.gov.justice.laa.dstew.access.enums.MatterType;

/**
 * Deserializer for MatterType enum.
 */
public class MatterTypeDeserializer extends GenericEnumDeserializer<MatterType> {
  public MatterTypeDeserializer() {
    super(MatterType.class);
  }


}
