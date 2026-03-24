package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullCfeSubmission {
    private String id;
    private String legalAidApplicationId;
    private String assessmentId;
    private String aasmState;
    private String errorMessage;
    private String cfeResult;
    private String createdAt;
    private String updatedAt;
}

