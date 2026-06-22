package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;

/** Orchestrates retrieval of a single application. */
@RequiredArgsConstructor
public class GetApplicationUseCase {

  private final GetApplicationApplicationGateway applicationGateway;

  /**
   * Retrieves a single application by id.
   *
   * @param id application id
   * @return application read model
   */
  @AllowApiCaseworker
  @Transactional
  public ApplicationReadModel execute(UUID id) {
    return applicationGateway
        .findApplicationReadModelById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", id)));
  }
}
