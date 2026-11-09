package uk.gov.justice.laa.dstew.access.query.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaries;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.query.utils.security.ReadAccessScope;

/**
 * Builds Application read models from the aggregate, and batch-loads the related read models needed
 * to build Application query results.
 */
@Component
@RequiredArgsConstructor
public class ApplicationReadModelAssembler {

  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final ApplicationSummaries applicationSummaries;
  private final PriorAuthorityReadRepository priorAuthorityReadRepository;

  /**
   * Builds a detached read model from a submitted Application's aggregate. Returns empty when the
   * aggregate is absent, still a draft, outside the caller's access, or its content is erased.
   * Group links, and changes made only by group events, come from the application summary; while
   * the summary lags they are empty.
   */
  public Optional<ApplicationReadModel> fromAggregate(
      @Nullable ApplicationAggregate aggregate, ReadAccessScope accessScope) {
    if (aggregate == null) {
      return Optional.empty();
    }
    ApplicationState state = aggregate.getState();
    if (state.getStatus() == null || state.getContent().isErased()) {
      return Optional.empty();
    }
    ApplicationDataPayload data = state.getContent().value();
    String officeCode = officeCode(data) != null ? officeCode(data) : state.getOfficeCode();
    if (!accessScope.permitsOfficeCode(officeCode)) {
      return Optional.empty();
    }
    ApplicationSummaryReadModel row =
        applicationSummaries.findById(state.getApplicationId()).orElse(null);
    ApplicationReadModel application =
        ApplicationReadModel.builder()
            .applicationId(state.getApplicationId())
            .status(state.getStatus())
            .applicationVersion(state.getApplicationVersion())
            .schemaVersion(state.getSchemaVersion())
            .officeCode(officeCode)
            .caseworkerId(state.getCaseworkerId())
            .createdAt(state.getCreatedAt())
            .submittedAt(state.getSubmittedAt())
            .modifiedAt(latest(state.getModifiedAt(), row == null ? null : row.getModifiedAt()))
            .linkedGroupId(row == null ? null : row.getLinkedGroupId())
            .leadApplicationId(row == null ? null : row.getLeadApplicationId())
            .uploadedDocuments(List.copyOf(state.getUploadedDocuments()))
            .potentialDuplicates(state.getPotentialDuplicates().orElse(null))
            .build();
    hydrate(application, data);
    application.setOfficeCode(officeCode);
    return Optional.of(application);
  }

  private ApplicationReadModel hydrate(
      ApplicationReadModel application, ApplicationDataPayload data) {
    application.setLaaReference(data.laaReference());
    application.setClient(data.client());
    application.setProvider(data.provider());
    application.setOfficeCode(officeCode(data));
    application.setOpponents(data.opponents());
    application.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    application.setCategoryOfLaw(data.categoryOfLaw());
    application.setMatterType(data.matterType());
    application.setCategoryOfLawCode(data.categoryOfLawCode());
    application.setMatterTypeCode(data.matterTypeCode());
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
        linkedGroup == null ? Map.of() : fetchLinkedMemberDetails(List.of(linkedGroup)));
  }

  /** Batch-fetches linked group read models, keyed by group ID. Null IDs are ignored. */
  public Map<UUID, LinkedApplicationGroupReadModel> fetchGroups(Collection<UUID> groupIds) {
    List<UUID> ids = groupIds.stream().filter(Objects::nonNull).distinct().toList();
    return groupReadRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(LinkedApplicationGroupReadModel::getGroupId, Function.identity()));
  }

  /** Batch-fetches prior authorities for the given Applications, keyed by Application ID. */
  public Map<UUID, List<PriorAuthorityReadModel>> fetchPriorAuthorities(
      Collection<UUID> applicationIds) {
    return priorAuthorityReadRepository.findAllByApplicationIdIn(applicationIds).stream()
        .collect(Collectors.groupingBy(PriorAuthorityReadModel::getApplicationId));
  }

  /** Batch-fetches required summary details for every linked group member, keyed by ID. */
  public Map<UUID, LinkedApplicationMemberDetails> fetchLinkedMemberDetails(
      Collection<LinkedApplicationGroupReadModel> groups) {
    List<UUID> memberIds =
        groups.stream().flatMap(group -> group.getMemberIds().stream()).distinct().toList();
    if (memberIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ApplicationSummaryReadModel> membersById = applicationSummaries.findByIds(memberIds);
    return memberIds.stream()
        .map(memberId -> toMemberDetailsEntry(memberId, membersById))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map.Entry<UUID, LinkedApplicationMemberDetails> toMemberDetailsEntry(
      UUID memberId, Map<UUID, ApplicationSummaryReadModel> membersById) {
    ApplicationSummaryReadModel member = membersById.get(memberId);
    if (member == null
        || member.getLaaReference() == null
        || member.getClientFirstName() == null
        || member.getClientLastName() == null) {
      throw new IllegalStateException(
          "Linked application summary details are incomplete for " + memberId);
    }
    return Map.entry(
        memberId,
        new LinkedApplicationMemberDetails(
            member.getLaaReference(),
            member.getStatus(),
            member.getClientFirstName(),
            member.getClientLastName()));
  }

  private static @Nullable Instant latest(@Nullable Instant first, @Nullable Instant second) {
    if (first == null) {
      return second;
    }
    return second == null || first.isAfter(second) ? first : second;
  }

  private static @Nullable String officeCode(ApplicationDataPayload data) {
    return data == null || data.provider() == null ? null : data.provider().getOfficeCode();
  }
}
