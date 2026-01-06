package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.mapper.deserializer.CategoryOfLawTypeDeserializer;
import uk.gov.justice.laa.dstew.access.mapper.deserializer.MatterTypeDeserializer;

/**
 * Record represents details of a proceeding.
 */
public record ProceedingDetail(boolean leadProceeding,
                               String id,
                               @JsonDeserialize(using = CategoryOfLawTypeDeserializer.class)
                               CategoryOfLaw categoryOfLaw,
                               @JsonDeserialize(using = MatterTypeDeserializer.class)
                               MatterType matterType,
                               Boolean useDelegatedFunctions) {
}
