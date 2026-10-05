package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** State object reconstructed by folding a PriorAuthority aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityState {
  UUID priorAuthorityId;
  UUID applicationId;
  String officeCode;
  long dataVersion;
  String requestFingerprint;
  boolean submitted;
  boolean decided;
  int schemaVersion;
  UUID caseworkerId;
  long assignmentVersion;
  String priorAuthorityType;
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();
}
