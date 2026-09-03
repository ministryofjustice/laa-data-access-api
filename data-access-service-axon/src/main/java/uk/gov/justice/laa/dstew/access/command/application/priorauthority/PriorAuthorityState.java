package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** State object reconstructed by folding a PriorAuthority aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityState {
  UUID priorAuthorityId;
  UUID applicationId;
  long dataVersion;
  String requestFingerprint;
  String status;
  int schemaVersion;
}
