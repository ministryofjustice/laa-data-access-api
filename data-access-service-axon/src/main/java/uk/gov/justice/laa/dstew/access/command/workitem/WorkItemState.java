package uk.gov.justice.laa.dstew.access.command.workitem;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** State object reconstructed by folding a WorkItem aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class WorkItemState {
  UUID workItemId;
  UUID caseworkerId;
}
