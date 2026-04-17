package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullProvider {
    private String id;
    private String username;
    private String type;
    private String roles;
    private String createdAt;
    private String updatedAt;
    private String officeCodes;
    private String firmId;
    private String selectedOfficeId;
    private String name;
    private String email;
    private Long ccmsContactId;
    private String silasId;
}

