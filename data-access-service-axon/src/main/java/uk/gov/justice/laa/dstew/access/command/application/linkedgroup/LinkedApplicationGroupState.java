package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
import java.util.UUID;
import lombok.NoArgsConstructor;

/** State object reconstructed by folding a linked-group aggregate's event stream. */
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupState {
  UUID groupId;
  UUID leadApplicationId;
  List<UUID> memberApplicationIds;
}
