package uk.gov.justice.laa.dstew.access.service.caseworkers;

import java.util.List;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.model.CaseworkerResponse;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured service for retrieving all caseworkers. */
@Service
public class GetCaseworkersService {

  private final CaseworkerRepository caseworkerRepository;

  public GetCaseworkersService(CaseworkerRepository caseworkerRepository) {
    this.caseworkerRepository = caseworkerRepository;
  }

  /** Returns all caseworkers. */
  @AllowApiCaseworker
  public List<CaseworkerResponse> getCaseworkers() {
    return caseworkerRepository.findAll().stream().map(this::toResponse).toList();
  }

  private CaseworkerResponse toResponse(Caseworker caseworker) {
    return CaseworkerResponse.builder()
        .id(caseworker.getId())
        .username(caseworker.getUsername())
        .build();
  }
}
