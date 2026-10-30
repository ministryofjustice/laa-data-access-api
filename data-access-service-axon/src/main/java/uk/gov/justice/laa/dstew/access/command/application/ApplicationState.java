package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** State object reconstructed by folding an aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationState {
  UUID applicationId;
  int schemaVersion;
  String requestFingerprint;
  String status;
  String officeCode;
  AutoGrantedState autoGranted = AutoGrantedState.PENDING;
  String overallDecision;
  long applicationDataVersion;
  long applicationVersion;
  UUID caseworkerId;
  long assignmentVersion;
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();

  /** Creates an otherwise empty state for an application with no events yet. */
  public static ApplicationState empty(UUID applicationId) {
    var state = new ApplicationState();
    state.applicationId = applicationId;
    return state;
  }
}
