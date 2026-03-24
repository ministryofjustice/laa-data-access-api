package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullScopeLimitation {
    private String id;
    private String scopeType;
    private String code;
    private String meaning;
    private String description;
    private String hearingDate;
    private String limitationNote;
    private String createdAt;
    private String updatedAt;
}

