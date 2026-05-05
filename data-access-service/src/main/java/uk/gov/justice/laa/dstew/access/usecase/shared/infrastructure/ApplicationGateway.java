package uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for application persistence operations. */
public interface ApplicationGateway {

  /** Full aggregate load — proceedings (with meritsDecision) and decision always populated. */
  ApplicationDomain loadById(UUID id);

  ApplicationDomain save(ApplicationDomain domain);

  boolean existsByApplyApplicationId(UUID applyApplicationId);

  Optional<ApplicationDomain> findByApplyApplicationId(UUID applyApplicationId);

  ApplicationDomain addLinkedApplication(ApplicationDomain lead, ApplicationDomain linked);
}
