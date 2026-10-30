package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemAssignmentTokensTest {
  @Test
  void givenItem_whenInitialTokenBuilt_thenDecodesToVersionZero() {
    UUID itemId = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    String token = WorkItemAssignmentTokens.initial(itemId);
    String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);

    assertEquals("v1:work-item-assignment:" + itemId + ":0", decoded);
  }
}
