package uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;

/** Gateway interface for proceeding persistence operations. */
public interface ProceedingGateway {
  void saveAll(UUID applicationId, List<ProceedingDomain> proceedings);
}
