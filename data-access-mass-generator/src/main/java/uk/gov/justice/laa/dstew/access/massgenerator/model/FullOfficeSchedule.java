package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullOfficeSchedule {
    private String id;
    private String officeId;
    private String areaOfLaw;
    private String categoryOfLaw;
    private String authorisationStatus;
    private String status;
    private String startDate;
    private String endDate;
    private Boolean cancelled;
    private Integer licenseIndicator;
    private String devolvedPowerStatus;
    private String createdAt;
    private String updatedAt;
}

