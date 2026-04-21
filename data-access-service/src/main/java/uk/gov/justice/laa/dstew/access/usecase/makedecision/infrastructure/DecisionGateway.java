package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;

/** Gateway interface for decision persistence operations. */
public interface DecisionGateway {
  DecisionDomain findByApplicationId(UUID applicationId);

  DecisionDomain saveAndLink(UUID applicationId, DecisionDomain decision);
}
