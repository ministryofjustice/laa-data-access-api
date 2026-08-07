package uk.gov.justice.laa.dstew.access.query.caseworker;

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;

/** Handles queries for the full list of caseworkers. */
@Component
public class CaseworkersQueryHandler {

  private final CaseworkerRepository caseworkerRepository;

  public CaseworkersQueryHandler(CaseworkerRepository caseworkerRepository) {
    this.caseworkerRepository = caseworkerRepository;
  }

  @QueryHandler
  public FindCaseworkersResult handle(FindCaseworkersQuery query) {
    return new FindCaseworkersResult(caseworkerRepository.findAll());
  }
}
