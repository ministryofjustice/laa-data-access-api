package uk.gov.justice.laa.dstew.access.controller.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.content.priorauthority.Apportionment;
import uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertCosts;
import uk.gov.justice.laa.dstew.access.content.priorauthority.ExpertDetails;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.util.EnumMapping;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Maps the generated HTTP request model to the Axon save-prior-authority-draft command. */
@Component
public class SavePriorAuthorityDraftCommandMapper {

  private static final String SCHEMA_VERSION_NAME = "PriorAuthority.json";

  private final ObjectMapper objectMapper;

  public SavePriorAuthorityDraftCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a save-draft command for a new draft, with a server-generated submission ID. */
  public CreatePriorAuthorityDraftCommand toCreateCommand(
      CreatePriorAuthorityDraftRequest request) {
    PriorAuthorityContent content =
        toContent(
            request.getPriorAuthorityType(),
            request.getJustification(),
            request.getExpertDetails(),
            request.getCounselDetails(),
            request.getDisbursementDetails());
    return new CreatePriorAuthorityDraftCommand(
        UUID.randomUUID(),
        request.getApplicationId(),
        content,
        serialise(request),
        1,
        SCHEMA_VERSION_NAME,
        Instant.now());
  }

  /** Creates a save-draft command for an existing draft identified by the given submission ID. */
  public UpdatePriorAuthorityDraftCommand toUpdateCommand(
      UUID priorAuthorityId, SavePriorAuthorityDraftRequest request) {
    PriorAuthorityContent content =
        toContent(
            request.getPriorAuthorityType(),
            request.getJustification(),
            request.getExpertDetails(),
            request.getCounselDetails(),
            request.getDisbursementDetails());
    return new UpdatePriorAuthorityDraftCommand(
        priorAuthorityId, content, serialise(request), 1, SCHEMA_VERSION_NAME, Instant.now());
  }

  private PriorAuthorityContent toContent(
      uk.gov.justice.laa.dstew.access.model.PriorAuthorityType priorAuthorityType,
      String justification,
      uk.gov.justice.laa.dstew.access.model.ExpertDetails expertDetails,
      uk.gov.justice.laa.dstew.access.model.CounselDetails counselDetails,
      uk.gov.justice.laa.dstew.access.model.DisbursementDetails disbursementDetails) {
    return new PriorAuthorityContent(
        EnumMapping.map(priorAuthorityType, PriorAuthorityType.class),
        justification,
        toExpertDetails(expertDetails),
        toCounselDetails(counselDetails),
        toDisbursementDetails(disbursementDetails));
  }

  private ExpertDetails toExpertDetails(
      uk.gov.justice.laa.dstew.access.model.ExpertDetails details) {
    if (details == null) {
      return null;
    }

    return new ExpertDetails(
        details.getExpertType(),
        details.getExpertFullName(),
        details.getExpertPostcode(),
        toExpertCosts(details.getExpertCosts()));
  }

  private ExpertCosts toExpertCosts(uk.gov.justice.laa.dstew.access.model.ExpertCosts costs) {
    if (costs == null) {
      return null;
    }

    return new ExpertCosts(
        EnumMapping.map(costs.getBillingType(), BillingType.class),
        toBigDecimal(costs.getHourlyRate()),
        toTimeRequested(costs.getTimeRequested()),
        toBigDecimal(costs.getTotalAmount()),
        costs.getCostsSharedWithOtherParties(),
        toApportionment(costs.getApportionment()));
  }

  private TimeRequested toTimeRequested(uk.gov.justice.laa.dstew.access.model.TimeRequested time) {
    if (time == null) {
      return null;
    }

    return new TimeRequested(time.getHours(), time.getMinutes());
  }

  private Apportionment toApportionment(
      uk.gov.justice.laa.dstew.access.model.Apportionment apportionment) {
    if (apportionment == null) {
      return null;
    }

    return new Apportionment(
        apportionment.getPartiesSharingCosts(), toBigDecimal(apportionment.getClientShareAmount()));
  }

  private CounselDetails toCounselDetails(
      uk.gov.justice.laa.dstew.access.model.CounselDetails details) {
    if (details == null) {
      return null;
    }

    return new CounselDetails(EnumMapping.map(details.getCounselType(), CounselType.class));
  }

  private DisbursementDetails toDisbursementDetails(
      uk.gov.justice.laa.dstew.access.model.DisbursementDetails details) {
    if (details == null) {
      return null;
    }

    return new DisbursementDetails(
        details.getDisbursementPurpose(), toBigDecimal(details.getDisbursementAmount()));
  }

  private BigDecimal toBigDecimal(Double value) {
    if (value == null) {
      return null;
    }

    return BigDecimal.valueOf(value);
  }

  private String serialise(Object request) {
    return RequestSerialiser.serialise(objectMapper, request);
  }
}
