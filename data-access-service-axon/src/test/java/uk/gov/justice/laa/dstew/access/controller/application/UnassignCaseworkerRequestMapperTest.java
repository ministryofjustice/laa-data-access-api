package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;

class UnassignCaseworkerRequestMapperTest {

  private final UnassignCaseworkerRequestMapper mapper =
      new UnassignCaseworkerRequestMapper(JsonMapper.builder().build());

  @Test
  void givenEventHistory_whenSerialised_thenPreservesDescription() {
    CaseworkerUnassignRequest request =
        CaseworkerUnassignRequest.builder()
            .eventHistory(EventHistoryRequest.builder().eventDescription("Returned").build())
            .build();

    assertThat(mapper.serialise(request)).contains("eventHistory", "Returned");
  }

  @Test
  void givenNoEventHistory_whenSerialised_thenPreservesAbsentHistory() throws Exception {
    var payload =
        JsonMapper.builder()
            .build()
            .readTree(mapper.serialise(CaseworkerUnassignRequest.builder().build()));

    assertThat(payload.get("eventHistory").isNull()).isTrue();
  }
}
