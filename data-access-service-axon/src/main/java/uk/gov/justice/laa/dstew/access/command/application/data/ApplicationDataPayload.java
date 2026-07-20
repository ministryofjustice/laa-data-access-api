package uk.gov.justice.laa.dstew.access.command.application.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;

/**
 * Sensitive application data stored outside the Axon event stream.
 *
 * <p>{@code applicationContent} is stored as a raw JSON map. PII sections (applicant,
 * applicationMerits, partner, etc.) are replaced inline with {@code "pii:<uuid>"} reference
 * strings before persistence, and rehydrated on read.
 */
public record ApplicationDataPayload(
    String laaReference,
    Map<String, Object> applicationContent,
    List<ApplicationIndividual> individuals,
    UUID applyApplicationId,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    List<ApplicationProceeding> proceedings,
    String serialisedRequest,
    String overallDecision,
    Boolean autoGranted,
    Map<UUID, ApplicationMeritsDecision> meritsDecisions,
    Map<String, Object> certificate,
    String decisionSerialisedRequest,
    String decisionEventDescription,
    String assignmentEventDescription) {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  /**
   * Creates a thin payload from creation details, converting {@code applicationContent} to a raw
   * map. PII sections are NOT externalised here; call {@link
   * ApplicationDataStore#append(java.util.UUID, long, ApplicationCreationDetails)} to externalise
   * and persist in one step.
   *
   * @param details the parsed application creation details
   * @return a payload with applicationContent as a raw map
   */
  public static ApplicationDataPayload from(ApplicationCreationDetails details) {
    Map<String, Object> contentMap =
        details.applicationContent() == null
            ? null
            : MAPPER.convertValue(
                details.applicationContent(), new TypeReference<Map<String, Object>>() {});
    return new ApplicationDataPayload(
        details.laaReference(),
        contentMap,
        details.individuals(),
        details.applyApplicationId(),
        details.submittedAt(),
        details.officeCode(),
        details.usedDelegatedFunctions(),
        details.categoryOfLaw(),
        details.matterType(),
        details.proceedings(),
        details.serialisedRequest(),
        null,
        null,
        Map.of(),
        null,
        null,
        null,
        null);
  }

  /** Returns a complete new data version containing the supplied decision state. */
  public ApplicationDataPayload withDecision(
      String newOverallDecision,
      Boolean newAutoGranted,
      Map<UUID, ApplicationMeritsDecision> newMeritsDecisions,
      Map<String, Object> newCertificate,
      String newDecisionSerialisedRequest,
      String newDecisionEventDescription) {
    return new ApplicationDataPayload(
        laaReference,
        applicationContent,
        individuals,
        applyApplicationId,
        submittedAt,
        officeCode,
        usedDelegatedFunctions,
        categoryOfLaw,
        matterType,
        proceedings,
        serialisedRequest,
        newOverallDecision,
        newAutoGranted,
        Map.copyOf(newMeritsDecisions),
        newCertificate == null ? null : Map.copyOf(newCertificate),
        newDecisionSerialisedRequest,
        newDecisionEventDescription,
        assignmentEventDescription);
  }

  /** Returns a complete new data version containing assignment audit details. */
  public ApplicationDataPayload withAssignment(String newAssignmentEventDescription) {
    return new ApplicationDataPayload(
        laaReference,
        applicationContent,
        individuals,
        applyApplicationId,
        submittedAt,
        officeCode,
        usedDelegatedFunctions,
        categoryOfLaw,
        matterType,
        proceedings,
        serialisedRequest,
        overallDecision,
        autoGranted,
        meritsDecisions,
        certificate,
        decisionSerialisedRequest,
        decisionEventDescription,
        newAssignmentEventDescription);
  }
}
