package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.deserializer.CategoryOfLawTypeDeserializer;
import uk.gov.justice.laa.dstew.access.deserializer.MatterTypeDeserializer;

/**
 * Record represents details of a proceeding.
 */
@Builder
public record Proceeding(boolean leadProceeding,
                         String id,
                         @JsonDeserialize(using = CategoryOfLawTypeDeserializer.class)
                               CategoryOfLaw categoryOfLaw,
                         @JsonDeserialize(using = MatterTypeDeserializer.class)
                               MatterType matterType,
                         Boolean useDelegatedFunctions) {
}
