package uk.gov.justice.laa.dstew.access.query.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadModel;
import uk.gov.justice.laa.dstew.access.query.application.listindex.ApplicationListIndexReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;

/**
 * Hydrates current-state rows with their referenced {@code application_data} version and
 * batch-loads the related read models needed to build Application query results.
 */
@Component
@RequiredArgsConstructor
public class ApplicationReadModelAssembler {

  private final ApplicationDataStore applicationDataStore;
  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final ApplicationListIndexReadRepository listIndexRepository;
  private final PriorAuthorityReadRepository priorAuthorityReadRepository;

  /** Hydrates a single row, or returns empty if its referenced data version is missing. */
  public Optional<ApplicationReadModel> hydrate(ApplicationReadModel application) {
    return hydrate(List.of(application)).stream().findFirst();
  }

  /**
   * Hydrates rows with a single batch load, preserving input order and dropping rows whose
   * referenced data version is missing.
   */
  public List<ApplicationReadModel> hydrate(List<ApplicationReadModel> applications) {
    List<ApplicationDataId> ids = applications.stream().map(this::dataId).toList();
    Map<ApplicationDataId, ApplicationDataPayload> dataById = applicationDataStore.getAll(ids);
    return applications.stream()
        .map(
            application -> {
              ApplicationDataPayload data = dataById.get(dataId(application));
              return data == null ? null : hydrate(application, data);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private ApplicationReadModel hydrate(
      ApplicationReadModel application, ApplicationDataPayload data) {
    application.setLaaReference(data.laaReference());
    application.setClient(data.client());
    application.setProvider(data.provider());
    application.setOfficeCode(officeCode(data));
    application.setOpponents(data.opponents());
    application.setSubmittedAt(data.submittedAt());
    application.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    application.setCategoryOfLaw(data.categoryOfLaw());
    application.setMatterType(data.matterType());
    application.setProceedings(data.proceedings());
    application.setDecisionStatus(data.overallDecision());
    application.setAutoGranted(data.autoGranted());
    application.setMeritsDecisions(data.meritsDecisions());
    application.setCertificate(data.certificate());
    application.setDocumentFilenames(data.documentFilenames());
    return application;
  }

  /** Builds the detail result for an already hydrated Application. */
  public ApplicationDetailResult assembleDetail(ApplicationReadModel application) {
    LinkedApplicationGroupReadModel linkedGroup =
        application.getLinkedGroupId() == null
            ? null
            : groupReadRepository.findById(application.getLinkedGroupId()).orElse(null);
    List<PriorAuthorityReadModel> priorAuthorities =
        priorAuthorityReadRepository.findAllByApplicationIdIn(
            List.of(application.getApplicationId()));
    return new ApplicationDetailResult(
        application,
        linkedGroup,
        priorAuthorities,
        findLinkedLaaReferences(application, linkedGroup));
  }

  /** Batch-fetches linked group read models for the given Applications, keyed by group ID. */
  public Map<UUID, LinkedApplicationGroupReadModel> fetchGroups(
      List<ApplicationReadModel> applications) {
    List<UUID> groupIds =
        applications.stream()
            .map(ApplicationReadModel::getLinkedGroupId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return groupReadRepository.findAllById(groupIds).stream()
        .collect(
            Collectors.toMap(LinkedApplicationGroupReadModel::getGroupId, Function.identity()));
  }

  /** Batch-fetches prior authorities for the given Applications, keyed by Application ID. */
  public Map<UUID, List<PriorAuthorityReadModel>> fetchPriorAuthorities(
      List<ApplicationReadModel> applications) {
    List<UUID> applicationIds =
        applications.stream().map(ApplicationReadModel::getApplicationId).toList();
    return priorAuthorityReadRepository.findAllByApplicationIdIn(applicationIds).stream()
        .collect(Collectors.groupingBy(PriorAuthorityReadModel::getApplicationId));
  }

  static String officeCode(ApplicationDataPayload data) {
    return data == null || data.provider() == null ? null : data.provider().getOfficeCode();
  }

  private Map<UUID, String> findLinkedLaaReferences(
      ApplicationReadModel application, LinkedApplicationGroupReadModel linkedGroup) {
    if (linkedGroup == null) {
      return Map.of();
    }
    List<UUID> otherMemberIds =
        linkedGroup.getMemberIds().stream()
            .filter(memberId -> !memberId.equals(application.getApplicationId()))
            .toList();
    if (otherMemberIds.isEmpty()) {
      return Map.of();
    }
    return listIndexRepository.findAllById(otherMemberIds).stream()
        .filter(member -> member.getLaaReference() != null)
        .collect(
            Collectors.toMap(
                ApplicationListIndexReadModel::getApplicationId,
                ApplicationListIndexReadModel::getLaaReference));
  }

  private ApplicationDataId dataId(ApplicationReadModel application) {
    return new ApplicationDataId(
        application.getApplicationId(), application.getApplicationDataVersion());
  }
}
