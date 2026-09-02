package uk.gov.justice.laa.dstew.access.controller.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.Apportionment;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CounselDetails;
import uk.gov.justice.laa.dstew.access.model.CounselType;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.PriorAuthorityResult;

/** Maps get-prior-authority use-case results to the public API response. */
@Component
public class GetPriorAuthorityResponseMapper {

  /** Converts a use-case result to its generated OpenAPI response model. */
  public PriorAuthorityResponse toResponse(PriorAuthorityResult result) {
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    response.setPriorAuthorityId(result.priorAuthorityId());
    response.setApplicationId(result.applicationId());
    response.setJustification(result.justification());
    response.setStatus(result.status());
    response.setPriorAuthorityType(
        result.priorAuthorityType() == null
            ? null
            : PriorAuthorityResponse.PriorAuthorityTypeEnum.valueOf(
                result.priorAuthorityType().name()));
    response.setExpertDetails(toExpertDetails(result.expertDetails()));
    response.setCounselDetails(toCounselDetails(result.counselDetails()));
    response.setDisbursementDetails(toDisbursementDetails(result.disbursementDetails()));
    return response;
  }

  private ExpertDetails toExpertDetails(
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.ExpertDetails
          details) {
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
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.ExpertCosts costs) {
    if (costs == null) {
      return null;
    }
    return new ExpertCosts()
        .billingType(
            costs.billingType() == null ? null : BillingType.valueOf(costs.billingType().name()))
        .hourlyRate(toDouble(costs.hourlyRate()))
        .timeRequested(toTimeRequested(costs.timeRequested()))
        .totalAmount(toDouble(costs.totalAmount()))
        .costsSharedWithOtherParties(costs.costsSharedWithOtherParties())
        .apportionment(toApportionment(costs.apportionment()));
  }

  private CounselDetails toCounselDetails(
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.CounselDetails
          details) {
    return details == null
        ? null
        : new CounselDetails()
            .counselType(
                details.counselType() == null
                    ? null
                    : CounselType.valueOf(details.counselType().name()));
  }

  private DisbursementDetails toDisbursementDetails(
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.DisbursementDetails
          details) {
    return details == null
        ? null
        : new DisbursementDetails()
            .disbursementPurpose(details.disbursementPurpose())
            .disbursementAmount(toDouble(details.disbursementAmount()));
  }

  private TimeRequested toTimeRequested(
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.TimeRequested
          timeRequested) {
    return timeRequested == null
        ? null
        : new TimeRequested().hours(timeRequested.hours()).minutes(timeRequested.minutes());
  }

  private Apportionment toApportionment(
      uk.gov.justice.laa.dstew.access.query.application.priorauthority.model.Apportionment
          apportionment) {
    return apportionment == null
        ? null
        : new Apportionment()
            .partiesSharingCosts(apportionment.partiesSharingCosts())
            .clientShareAmount(toDouble(apportionment.clientShareAmount()));
  }

  private Double toDouble(java.math.BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
