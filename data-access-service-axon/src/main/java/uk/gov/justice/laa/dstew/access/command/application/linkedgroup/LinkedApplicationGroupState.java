package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** State object reconstructed by folding a linked-group aggregate's event stream. */
@NoArgsConstructor
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupState {
  UUID groupId;
  UUID leadApplicationId;
  List<UUID> memberApplicationIds;
  long groupVersion;
  boolean dissolved;

  /** Returns the member application IDs, or an empty list before the group is established. */
  public List<UUID> memberIdsOrEmpty() {
    return memberApplicationIds == null ? List.of() : memberApplicationIds;
  }
}
