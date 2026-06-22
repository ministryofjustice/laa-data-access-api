package uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;

/** Gateway for loading a single application read model. */
public interface GetApplicationApplicationGateway {

  /**
   * Finds an application read model by id.
   *
   * @param id application id
   * @return optional application read model
   */
  Optional<ApplicationReadModel> findApplicationReadModelById(UUID id);
}
