package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Apportionment;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CounselDetails;
import uk.gov.justice.laa.dstew.access.model.CounselType;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;

/** Retrieves and hydrates the current Prior Authority submission. */
@Service
public class GetPriorAuthorityUseCase {

  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final QueryGateway queryGateway;

  public GetPriorAuthorityUseCase(
      PriorAuthorityDataStore priorAuthorityDataStore, QueryGateway queryGateway) {
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.queryGateway = queryGateway;
  }

  /** Retrieves the Prior Authority identified by its submission ID. */
  public PriorAuthorityResponse getPriorAuthority(UUID priorAuthorityId) {
    PriorAuthorityReadModel priorAuthority =
        queryGateway
            .query(
                new FindPriorAuthorityBySubmissionIdQuery(priorAuthorityId),
                PriorAuthorityReadModel.class)
            .join();
    if (priorAuthority == null) {
      throw new ResourceNotFoundException("No prior authority found with ID: " + priorAuthorityId);
    }

    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(priorAuthorityId, priorAuthority.getDataVersion());
    return toResponse(priorAuthority, payload.content());
  }

  private PriorAuthorityResponse toResponse(
      PriorAuthorityReadModel priorAuthority, PriorAuthorityContent content) {
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    response.setPriorAuthorityId(priorAuthority.getSubmissionId());
    response.setApplicationId(priorAuthority.getApplicationId());

    response.setJustification(content.justification());
    response.setStatus(priorAuthority.getStatus());

    if (content.priorAuthorityType() == null || content.priorAuthorityType().isEmpty()) {
      response.setPriorAuthorityType(null);
      response.setCounselDetails(null);
      response.setDisbursementDetails(null);
      response.setExpertDetails(null);
      return response;
    }

    PriorAuthorityResponse.PriorAuthorityTypeEnum priorAuthorityType =
        PriorAuthorityResponse.PriorAuthorityTypeEnum.fromValue(content.priorAuthorityType());
    response.setPriorAuthorityType(priorAuthorityType);
    return switch (priorAuthorityType) {
      case PriorAuthorityResponse.PriorAuthorityTypeEnum.EXPERT -> {
        response.setExpertDetails(toExpertDetails(content));
        yield response;
      }
      case PriorAuthorityResponse.PriorAuthorityTypeEnum.COUNSEL -> {
        response.setCounselDetails(toCounselDetails(content));
        yield response;
      }
      case PriorAuthorityResponse.PriorAuthorityTypeEnum.DISBURSEMENT -> {
        response.setDisbursementDetails(toDisbursementDetails(content));
        yield response;
      }
    };
  }

  private ExpertDetails toExpertDetails(PriorAuthorityContent content) {
    if (content.expertDetails() == null) {
      return null;
    }
    return new ExpertDetails()
        .expertType(content.expertDetails().expertType())
        .expertFullName(content.expertDetails().expertFullName())
        .expertPostcode(content.expertDetails().expertPostcode())
        .expertCosts(
            content.expertDetails().expertCosts() == null
                ? null
                : new uk.gov.justice.laa.dstew.access.model.ExpertCosts()
                    .billingType(
                        BillingType.fromValue(content.expertDetails().expertCosts().billingType()))
                    .hourlyRate(toDouble(content.expertDetails().expertCosts().hourlyRate()))
                    .timeRequested(toTimeRequested(content))
                    .totalAmount(toDouble(content.expertDetails().expertCosts().totalAmount()))
                    .costsSharedWithOtherParties(
                        content.expertDetails().expertCosts().costsSharedWithOtherParties())
                    .apportionment(toApportionment(content)));
  }

  private CounselDetails toCounselDetails(PriorAuthorityContent content) {
    if (content.counselDetails() == null) {
      return null;
    }
    return new CounselDetails()
        .counselType(CounselType.fromValue(content.counselDetails().counselType()));
  }

  private DisbursementDetails toDisbursementDetails(PriorAuthorityContent content) {
    if (content.disbursementDetails() == null) {
      return null;
    }
    return new DisbursementDetails()
        .disbursementPurpose(content.disbursementDetails().disbursementPurpose())
        .disbursementAmount(toDouble(content.disbursementDetails().disbursementAmount()));
  }

  private TimeRequested toTimeRequested(PriorAuthorityContent content) {
    uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested timeRequested =
        content.expertDetails().expertCosts().timeRequested();
    if (timeRequested == null) {
      return null;
    }
    return new TimeRequested().hours(timeRequested.hours()).minutes(timeRequested.minutes());
  }

  private Apportionment toApportionment(PriorAuthorityContent content) {
    uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment apportionment =
        content.expertDetails().expertCosts().apportionment();
    if (apportionment == null) {
      return null;
    }
    return new Apportionment()
        .partiesSharingCosts(apportionment.partiesSharingCosts())
        .clientShareAmount(toDouble(apportionment.clientShareAmount()));
  }

  private Double toDouble(java.math.BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
