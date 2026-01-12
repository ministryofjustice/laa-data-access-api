package uk.gov.justice.laa.dstew.access.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Class represents the content of an application.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationContentDetails {
  private String id;
  private Instant submittedAt;
  private boolean autoGrant;
  private List<ProceedingDetails> proceedings;

}
