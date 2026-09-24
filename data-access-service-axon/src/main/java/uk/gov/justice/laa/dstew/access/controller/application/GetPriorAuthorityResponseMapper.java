package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.model.Apportionment;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CounselDetails;
import uk.gov.justice.laa.dstew.access.model.CounselType;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDecisionDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;
import uk.gov.justice.laa.dstew.access.model.UploadedDocument;

/** Maps get-prior-authority use-case results to the public API response. */
@Component
public class GetPriorAuthorityResponseMapper {

  /** Converts a use-case result to its generated OpenAPI response model. */
  public PriorAuthorityResponse toResponse(PriorAuthorityResult result) {
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    response.setPriorAuthorityId(result.priorAuthorityId());
    response.setApplicationId(result.applicationId());
    response.setJustification(result.justification());
    response.setStatus(
        result.status() == null
            ? null
            : PriorAuthorityResponse.StatusEnum.fromValue(result.status()));
    response.setDecision(
        result.decisionDetails() == null ? null : toDecision(result.decisionDetails().decision()));
    response.setDecisionJustification(
        result.decisionDetails() == null ? null : result.decisionDetails().decisionJustification());
    response.setDecisionDetails(toDecisionDetails(result.decisionDetails()));
    response.setPriorAuthorityType(
        result.priorAuthorityType() == null
            ? null
            : PriorAuthorityResponse.PriorAuthorityTypeEnum.valueOf(
                result.priorAuthorityType().name()));
    response.setExpertDetails(toExpertDetails(result.expertDetails()));
    response.setCounselDetails(toCounselDetails(result.counselDetails()));
    response.setDisbursementDetails(toDisbursementDetails(result.disbursementDetails()));
    response.setUploadedDocuments(toUploadedDocuments(result));
    return response;
  }

  private List<UploadedDocument> toUploadedDocuments(PriorAuthorityResult result) {
    if (result.uploadedDocuments() == null) {
      return null;
    }
    return result.uploadedDocuments().stream()
        .map(
            document ->
                new UploadedDocument()
                    .documentId(document.documentId())
                    .documentType(
                        document.documentType() == null
                            ? null
                            : PriorAuthorityDocumentType.fromValue(document.documentType()))
                    .fileName(document.fileName())
                    .fileType(document.fileType())
                    .mediaType(document.mediaType())
                    .size(document.size())
                    .uploadedAt(
                        document.uploadedAt() == null
                            ? null
                            : document.uploadedAt().atOffset(java.time.ZoneOffset.UTC))
                    .sourceService(document.sourceService())
                    .checksum(document.checksum()))
        .toList();
  }

  private ExpertDetails toExpertDetails(
      uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails details) {
    if (details == null) {
      return null;
    }
    return new ExpertDetails()
        .expertType(details.expertType())
        .expertFullName(details.expertFullName())
        .expertPostcode(details.expertPostcode())
        .expertCosts(toExpertCosts(details.expertCosts()));
  }

  private ExpertCosts toExpertCosts(
      uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts costs) {
    if (costs == null) {
      return null;
    }
    return new ExpertCosts()
        .billingType(
            costs.billingType() == null ? null : BillingType.valueOf(costs.billingType().name()))
        .hourlyRate(costs.hourlyRate())
        .timeRequested(toTimeRequested(costs.timeRequested()))
        .totalAmount(costs.totalAmount())
        .costsSharedWithOtherParties(costs.costsSharedWithOtherParties())
        .apportionment(toApportionment(costs.apportionment()));
  }

  private CounselDetails toCounselDetails(
      uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails details) {
    return details == null
        ? null
        : new CounselDetails()
            .counselType(
                details.counselType() == null
                    ? null
                    : CounselType.valueOf(details.counselType().name()));
  }

  private DisbursementDetails toDisbursementDetails(
      uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails details) {
    return details == null
        ? null
        : new DisbursementDetails()
            .disbursementPurpose(details.disbursementPurpose())
            .disbursementAmount(details.disbursementAmount());
  }

  private TimeRequested toTimeRequested(
      uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested timeRequested) {
    return timeRequested == null
        ? null
        : new TimeRequested().hours(timeRequested.hours()).minutes(timeRequested.minutes());
  }

  private Apportionment toApportionment(
      uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment apportionment) {
    return apportionment == null
        ? null
        : new Apportionment()
            .partiesSharingCosts(apportionment.partiesSharingCosts())
            .clientShareAmount(apportionment.clientShareAmount());
  }

  private PriorAuthorityResponse.DecisionEnum toDecision(String decision) {
    if (decision == null || decision.isBlank()) {
      return null;
    }
    try {
      return PriorAuthorityResponse.DecisionEnum.valueOf(decision.trim());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private PriorAuthorityDecisionDetails.DecisionEnum toDecisionDetailsEnum(String decision) {
    if (decision == null || decision.isBlank()) {
      return null;
    }
    try {
      return PriorAuthorityDecisionDetails.DecisionEnum.fromValue(decision.trim());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private PriorAuthorityDecisionDetails toDecisionDetails(
      uk.gov.justice.laa.dstew.access.command.application.priorauthority.data
              .PriorAuthorityDataPayload.DecisionDetails
          details) {
    if (details == null) {
      return null;
    }
    PriorAuthorityDecisionDetails result = new PriorAuthorityDecisionDetails();
    if (details.decision() != null) {
      result.setDecision(toDecisionDetailsEnum(details.decision()));
    }
    if (details.decisionJustification() != null) {
      result.setDecisionJustification(details.decisionJustification());
    }
    if (details.amountGranted() != null) {
      result.setAmountGranted(details.amountGranted());
    }
    if (details.dateGranted() != null) {
      result.setDateGranted(details.dateGranted().atOffset(java.time.ZoneOffset.UTC));
    }
    if (details.expert() != null) {
      uk.gov.justice.laa.dstew.access.model.ExpertFeeInformation expertInfo =
          new uk.gov.justice.laa.dstew.access.model.ExpertFeeInformation();
      expertInfo.setNewFixedRateAmount(details.expert().getNewFixedRateAmount());
      expertInfo.setNewHourlyRateAmount(details.expert().getNewHourlyRateAmount());
      result.setExpert(expertInfo);
    }
    if (details.disbursement() != null) {
      uk.gov.justice.laa.dstew.access.model.DisbursementInformation disbursementInfo =
          new uk.gov.justice.laa.dstew.access.model.DisbursementInformation();
      disbursementInfo.setNewAmount(details.disbursement().getNewAmount());
      result.setDisbursement(disbursementInfo);
    }
    if (details.apportionment() != null) {
      Apportionment apportionmentInfo = new Apportionment();
      // apportionmentInfo.setPartiesSharingCosts(...) // Not available in command object
      apportionmentInfo.setClientShareAmount(details.apportionment().getNewClientShareAmount());
      result.setApportionment(apportionmentInfo);
    }
    return result;
  }
}
