package uk.gov.justice.laa.dstew.access.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
* Data object to represent the state of an application.
*/
@Entity
@Getter
@Setter
@Table(name = "status_code_lookup")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StatusCodeLookupEntity {
  @Id
  @Column(columnDefinition = "UUID")
  private UUID id;
    
  @Column(name = "code")
  private String code;

  @Column(name = "description")
  private String description;
}
