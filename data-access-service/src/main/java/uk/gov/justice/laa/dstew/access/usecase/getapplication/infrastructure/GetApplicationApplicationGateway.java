package uk.gov.justice.laa.dstew.access.usecase.getapplication.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;

/** Gateway for loading a single application DB projection. */
public interface GetApplicationApplicationGateway {

  /**
   * Finds an application DB projection by id.
   *
   * @param id application id
   * @return optional application DB projection
   */
  Optional<ApplicationDbProjection> findApplicationById(UUID id);
}
