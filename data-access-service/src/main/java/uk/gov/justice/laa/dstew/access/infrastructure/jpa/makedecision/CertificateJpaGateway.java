package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.CertificateGateway;

/** JPA implementation of CertificateGateway. No @Component — wired via MakeDecisionConfig. */
public class CertificateJpaGateway implements CertificateGateway {

  private final CertificateRepository certificateRepository;

  public CertificateJpaGateway(CertificateRepository certificateRepository) {
    this.certificateRepository = certificateRepository;
  }

  @Override
  public void saveOrUpdate(UUID applicationId, Map<String, Object> content) {
    CertificateEntity entity =
        certificateRepository
            .findByApplicationId(applicationId)
            .map(
                existing -> {
                  existing.setCertificateContent(content);
                  return existing;
                })
            .orElseGet(
                () ->
                    CertificateEntity.builder()
                        .applicationId(applicationId)
                        .certificateContent(content)
                        .build());
    certificateRepository.save(entity);
  }

  @Override
  public void deleteByApplicationId(UUID applicationId) {
    certificateRepository.deleteByApplicationId(applicationId);
  }

  @Override
  public boolean existsByApplicationId(UUID applicationId) {
    return certificateRepository.existsByApplicationId(applicationId);
  }
}
