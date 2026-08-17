package uk.gov.justice.laa.dstew.access.controller.caseworker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.query.caseworker.FindCaseworkersResult;

class GetCaseworkersResponseMapperTest {

  private final GetCaseworkersResponseMapper mapper = new GetCaseworkersResponseMapper();

  @Test
  void givenCaseworkers_whenMapped_thenPopulatesIdAndUsername() {
    UUID id = UUID.randomUUID();
    Caseworker caseworker = new Caseworker(id, "alice@example.com");

    List<CaseworkerResponse> response =
        mapper.toResponse(new FindCaseworkersResult(List.of(caseworker)));

    assertThat(response)
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getId()).isEqualTo(id);
              assertThat(mapped.getUsername()).isEqualTo("alice@example.com");
            });
  }

  @Test
  void givenNoCaseworkers_whenMapped_thenReturnsEmptyList() {
    List<CaseworkerResponse> response = mapper.toResponse(new FindCaseworkersResult(List.of()));

    assertThat(response).isEmpty();
  }
}
