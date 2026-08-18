package uk.gov.justice.laa.dstew.access.controller.caseworker;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.query.caseworker.FindCaseworkersResult;

/** Maps caseworker query results to the public API response. */
@Component
public class GetCaseworkersResponseMapper {

  public List<CaseworkerResponse> toResponse(FindCaseworkersResult result) {
    return result.caseworkers().stream().map(this::toCaseworkerResponse).toList();
  }

  private CaseworkerResponse toCaseworkerResponse(Caseworker caseworker) {
    return new CaseworkerResponse().id(caseworker.getId()).username(caseworker.getUsername());
  }
}
