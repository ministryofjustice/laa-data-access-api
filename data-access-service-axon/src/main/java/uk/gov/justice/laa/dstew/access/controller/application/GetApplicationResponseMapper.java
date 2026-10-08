package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationAddress;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.Opponent;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.applicationcontent.ScopeLimitation;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.model.AddressResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationDocumentResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.AutoGranted;
import uk.gov.justice.laa.dstew.access.model.ClientResponse;
import uk.gov.justice.laa.dstew.access.model.DecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.LinkedApplicationMemberDetails;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

/** Maps the typed current-state projection to the public application response. */
@Component
public class GetApplicationResponseMapper {

  /** Builds a response without reparsing content from JSON. */
  public ApplicationResponse toResponse(ApplicationReadModel application) {
    return toResponse(application, null, List.of(), Map.of());
  }

  /** Builds a response from the application and its related projection rows. */
  public ApplicationResponse toResponse(
      ApplicationReadModel application,
      LinkedApplicationGroupReadModel linkedGroup,
      List<PriorAuthorityReadModel> priorAuthorities,
      Map<UUID, LinkedApplicationMemberDetails> linkedMemberDetails) {
    ApplicationResponse response = new ApplicationResponse();
    response.setApplicationId(application.getApplicationId());
    response.setStatus(ApplicationStatus.valueOf(application.getStatus()));
    response.setLaaReference(application.getLaaReference());
    if (application.getCreatedAt() == null) {
      throw new IllegalStateException("Application createdAt is missing from its read model");
    }
    response.setCreatedAt(application.getCreatedAt().atOffset(ZoneOffset.UTC));
    response.setLastUpdated(application.getModifiedAt().atOffset(ZoneOffset.UTC));
    if (application.getSubmittedAt() == null) {
      throw new IllegalStateException("Application submittedAt is missing from its read model");
    }
    response.setSubmittedAt(application.getSubmittedAt().atOffset(ZoneOffset.UTC));
    response.setIsLead(
        linkedGroup != null
            && application.getApplicationId().equals(linkedGroup.getLeadApplicationId()));
    response.setLinkedApplications(
        toLinkedSummaries(application, linkedGroup, linkedMemberDetails));
    response.setAssignedTo(application.getCaseworkerId());
    response.setUsedDelegatedFunctions(application.getUsedDelegatedFunctions());
    response.setAutoGranted(AutoGranted.valueOf(application.getAutoGranted().name()));
    response.setDecisionStatus(
        application.getDecisionStatus() == null
            ? DecisionStatusResponse.PENDING
            : DecisionStatusResponse.valueOf(application.getDecisionStatus()));
    response.setVersion(application.getApplicationVersion());
    response.setLinkedGroupVersion(LinkedGroupVersionTokens.encode(linkedGroup));
    response.setProvider(toProvider(application));
    response.setClient(toClient(application.getClient()));
    response.setOpponents(toOpponents(application.getOpponents()));
    response.setProceedings(
        toProceedings(application.getProceedings(), application.getMeritsDecisions()));
    response.setPotentialDuplicates(application.getPotentialDuplicates());
    response.setPriorAuthorities(PriorAuthoritySummaryMapper.toSummaries(priorAuthorities));
    response.setUploadedDocuments(toUploadedDocuments(application));
    return response;
  }

  private ClientResponse toClient(ApplicationClient client) {
    return ClientResponse.builder()
        .firstName(client.getFirstName())
        .lastName(client.getLastName())
        .lastNameAtBirth(client.getLastNameAtBirth())
        .dateOfBirth(client.getDateOfBirth())
        .hasNationalInsuranceNumber(client.getHasNationalInsuranceNumber())
        .nationalInsuranceNumber(client.getNationalInsuranceNumber())
        .appliedPreviously(client.getAppliedPreviously())
        .previousApplicationId(client.getPreviousApplicationId())
        .relationshipToInvolvedChildren(client.getRelationshipToInvolvedChildren())
        .addresses(client.getAddresses().stream().map(this::toAddress).toList())
        .build();
  }

  private AddressResponse toAddress(ApplicationAddress address) {
    return AddressResponse.builder()
        .addressLineOne(address.getAddressLineOne())
        .addressLineTwo(address.getAddressLineTwo())
        .addressLineThree(address.getAddressLineThree())
        .city(address.getCity())
        .county(address.getCounty())
        .postcode(address.getPostcode())
        .organisation(address.getOrganisation())
        .buildingNumberName(address.getBuildingNumberName())
        .countryCode(address.getCountryCode())
        .countryName(address.getCountryName())
        .location(address.getLocation())
        .lookupUsed(address.getLookupUsed())
        .careOf(address.getCareOf())
        .careOfFirstName(address.getCareOfFirstName())
        .careOfLastName(address.getCareOfLastName())
        .careOfOrganisationName(address.getCareOfOrganisationName())
        .build();
  }

  private List<ApplicationDocumentResponse> toUploadedDocuments(ApplicationReadModel application) {
    if (application.getUploadedDocuments() == null) {
      return List.of();
    }
    Map<UUID, String> filenames =
        application.getDocumentFilenames() == null ? Map.of() : application.getDocumentFilenames();
    return application.getUploadedDocuments().stream()
        .filter(document -> !document.deleted())
        .sorted(
            Comparator.comparing(UploadDocument::uploadedAt)
                .thenComparing(UploadDocument::documentId))
        .map(
            document ->
                new ApplicationDocumentResponse()
                    .documentId(document.documentId())
                    .documentType(document.documentType())
                    .fileName(filenames.get(document.documentId()))
                    .uploadedAt(document.uploadedAt().atOffset(ZoneOffset.UTC))
                    .size(document.size())
                    .contentType(document.contentType())
                    .checksum(document.checksum())
                    .sourceService(document.sourceService()))
        .toList();
  }

  private ProviderResponse toProvider(ApplicationReadModel application) {
    ApplicationProvider provider = application.getProvider();
    String contactEmail = provider != null ? provider.getContactEmail() : null;
    String officeCode = application.getOfficeCode();
    if (officeCode == null && contactEmail == null) {
      return null;
    }
    return ProviderResponse.builder().officeCode(officeCode).contactEmail(contactEmail).build();
  }

  private List<OpponentResponse> toOpponents(List<Opponent> opponents) {
    if (opponents == null) {
      return Collections.emptyList();
    }
    return opponents.stream().map(this::toOpponent).toList();
  }

  private OpponentResponse toOpponent(Opponent opponent) {
    return OpponentResponse.builder()
        .opponentType(opponent.getOpponentType())
        .firstName(opponent.getFirstName())
        .lastName(opponent.getLastName())
        .organisationName(opponent.getOrganisationName())
        .build();
  }

  private List<ApplicationProceedingResponse> toProceedings(
      List<Proceeding> proceedings, Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    if (proceedings == null) {
      return Collections.emptyList();
    }
    return proceedings.stream()
        .map(proceeding -> toProceeding(proceeding, meritsDecisions))
        .toList();
  }

  private ApplicationProceedingResponse toProceeding(
      Proceeding proceeding, Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    ApplicationMeritsDecision meritsDecision =
        meritsDecisions == null ? null : meritsDecisions.get(proceeding.getId());
    return ApplicationProceedingResponse.builder()
        .proceedingId(proceeding.getId())
        .leadProceeding(proceeding.getLeadProceeding())
        .code(proceeding.getCode())
        .meaning(proceeding.getMeaning())
        .description(proceeding.getDescription())
        .matterType(proceeding.getMatterType())
        .matterTypeCode(proceeding.getMatterTypeCode())
        .categoryOfLaw(proceeding.getCategoryOfLaw())
        .categoryOfLawCode(proceeding.getCategoryOfLawCode())
        .clientInvolvementType(proceeding.getClientInvolvementType())
        .clientInvolvementTypeCode(proceeding.getClientInvolvementTypeCode())
        .usedDelegatedFunctions(proceeding.getUsedDelegatedFunctions())
        .delegatedFunctionsDate(proceeding.getDelegatedFunctionsDate())
        .delegatedFunctionsCostLimitation(proceeding.getDelegatedFunctionsCostLimitation())
        .substantiveLevelOfServiceCode(proceeding.getSubstantiveLevelOfServiceCode())
        .substantiveLevelOfServiceName(proceeding.getSubstantiveLevelOfServiceName())
        .emergencyLevelOfServiceCode(proceeding.getEmergencyLevelOfServiceCode())
        .emergencyLevelOfServiceName(proceeding.getEmergencyLevelOfServiceName())
        .substantiveCostLimitation(proceeding.getSubstantiveCostLimitation())
        .meritsDecision(
            meritsDecision == null || meritsDecision.decision() == null
                ? MeritsDecisionStatusResponse.PENDING
                : MeritsDecisionStatusResponse.valueOf(meritsDecision.decision()))
        .scopeLimitations(toScopeLimitations(proceeding.getScopeLimitations()))
        .involvedChildren(toInvolvedChildren(proceeding))
        .build();
  }

  private List<ScopeLimitationResponse> toScopeLimitations(List<ScopeLimitation> scopeLimitations) {
    if (scopeLimitations == null) {
      return Collections.emptyList();
    }
    return scopeLimitations.stream()
        .map(
            scopeLimitation ->
                ScopeLimitationResponse.builder()
                    .code(scopeLimitation.getCode())
                    .type(scopeLimitation.getType())
                    .meaning(scopeLimitation.getMeaning())
                    .description(scopeLimitation.getDescription())
                    .build())
        .toList();
  }

  private List<InvolvedChildResponse> toInvolvedChildren(Proceeding proceeding) {
    if (proceeding.getInvolvedChildren() == null) {
      return Collections.emptyList();
    }
    return proceeding.getInvolvedChildren().stream()
        .map(
            child ->
                new InvolvedChildResponse()
                    .firstName(child.getFirstName())
                    .lastName(child.getLastName())
                    .dateOfBirth(child.getDateOfBirth()))
        .toList();
  }

  private List<LinkedApplicationSummaryResponse> toLinkedSummaries(
      ApplicationReadModel application,
      LinkedApplicationGroupReadModel linkedGroup,
      Map<UUID, LinkedApplicationMemberDetails> linkedMemberDetails) {
    if (linkedGroup == null) {
      return Collections.emptyList();
    }
    return linkedGroup.getMemberIds().stream()
        .filter(memberId -> !memberId.equals(application.getApplicationId()))
        .map(
            memberId -> {
              LinkedApplicationMemberDetails member = linkedMemberDetails.get(memberId);
              if (member == null) {
                throw new IllegalStateException(
                    "Linked application details are missing for " + memberId);
              }
              LinkedApplicationSummaryResponse linked = new LinkedApplicationSummaryResponse();
              linked.setApplicationId(memberId);
              linked.setLaaReference(member.laaReference());
              linked.setIsLead(memberId.equals(linkedGroup.getLeadApplicationId()));
              linked.setClientFirstName(member.clientFirstName());
              linked.setClientLastName(member.clientLastName());
              return linked;
            })
        .toList();
  }
}
