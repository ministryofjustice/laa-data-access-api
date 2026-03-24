package uk.gov.justice.laa.dstew.access.massgenerator.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullProceedingMerits {
    private String opponentsApplication;
    private String attemptsToSettle;
    private String specificIssue;
    private String varyOrder;
    private String chancesOfSuccess;
    private String prohibitedSteps;
    private String childCareAssessment;
    private List<Object> proceedingLinkedChildren;
    private List<Object> involvedChildren;
}

