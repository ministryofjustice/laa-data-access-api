package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import java.util.Map;
import java.util.UUID;

/** Gateway interface for certificate persistence operations. */
public interface CertificateGateway {
  void saveOrUpdate(UUID applicationId, Map<String, Object> content);

  void deleteByApplicationId(UUID applicationId);

  boolean existsByApplicationId(UUID applicationId);
}
