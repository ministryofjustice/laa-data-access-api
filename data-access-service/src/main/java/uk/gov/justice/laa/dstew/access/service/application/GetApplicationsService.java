package uk.gov.justice.laa.dstew.access.service.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;

/** Service to get applications. */
@RequiredArgsConstructor
@Service
public class GetApplicationsService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ProceedingMapper proceedingMapper;
  private final ProceedingRepository proceedingRepository;

  /**
   * Retrieve a single application by ID.
   *
   * @param id application UUID
   * @return application DTO
   */
  @AllowApiCaseworker
  public ApplicationResponse getApplication(final UUID id) {

    final ApplicationEntity entity =
        ApplicationServiceHelper.getExistingApplication(id, applicationRepository);
    ApplicationResponse application = applicationMapper.toApplication(entity);

    Set<ProceedingEntity> proceedings = proceedingRepository.findAllByApplicationId(id);

    if (proceedings != null) {

      proceedings.forEach(
          proceeding -> {
            ApplicationProceedingResponse applicationProceedingResponse =
                proceedingMapper.toApplicationProceeding(proceeding);

            // List<Map<String, Object>> involvedChildren = getInvolvedChildren(entity);
            // if (involvedChildren != null) {
            //   List<Object> children = new ArrayList<>();
            //   involvedChildren.forEach(children::add);
            //   applicationProceeding.setInvolvedChildren(children);
            // }
            // else {
            //   applicationProceeding.setInvolvedChildren(null);
            // }

            if (entity.getDecision() != null) {
              Optional<MeritsDecisionEntity> meritsDecision =
                  entity.getDecision().getMeritsDecisions().stream()
                      .filter(m -> m.getProceeding().getId().equals(proceeding.getId()))
                      .findFirst();

              meritsDecision.ifPresent(
                  meritsDecisionEntity ->
                      applicationProceedingResponse.setMeritsDecision(
                          meritsDecisionEntity.getDecision()));
            }
            application.getProceedings().add(applicationProceedingResponse);
          });
    }

    return application;
  }
}
