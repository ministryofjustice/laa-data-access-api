package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure.GetApplicationApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;

/** Orchestrates retrieval of a single application. */
@RequiredArgsConstructor
public class GetApplicationUseCase {

  private final GetApplicationApplicationGateway applicationGateway;
  private final GetApplicationReadModelMapper readModelMapper;

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
        .findApplicationById(id)
        .map(readModelMapper::toApplicationReadModel)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", id)));
  }
}
