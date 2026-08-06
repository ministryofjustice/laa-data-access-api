package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** State object reconstructed by folding an aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationState {
  UUID applicationId;
  boolean isAssociatedMember;
  int schemaVersion;
  String requestFingerprint;
  long applicationDataVersion;
  long applicationVersion;
}
