package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.deserializer.CategoryOfLawTypeDeserializer;
import uk.gov.justice.laa.dstew.access.deserializer.MatterTypeDeserializer;

/**
 * Record represents details of a proceeding.
 */
@Builder(toBuilder = true)
public record ProceedingDetails(
    boolean leadProceeding,
    UUID id,
    @JsonDeserialize(using = CategoryOfLawTypeDeserializer.class)
    CategoryOfLaw categoryOfLaw,
    @JsonDeserialize(using = MatterTypeDeserializer.class)
    MatterType matterType,
    Boolean usedDelegatedFunctions) {
}
