package uk.gov.justice.laa.dstew.access.command.application.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContent;
import uk.gov.justice.laa.dstew.access.applicationcontent.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.applicationcontent.MatterType;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;

/** Sensitive application data stored outside the Axon event stream. */
public record ApplicationDataPayload(
    String laaReference,
    ApplicationContent applicationContent,
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
    String assignmentEventDescription,
    List<ApplicationNote> notes) {

  /**
   * Normalises a null {@code notes} list to an empty list so that existing {@code application_data}
   * JSONB rows (which pre-date this field) deserialise cleanly when Jackson passes {@code null} for
   * the absent key.
   */
  public ApplicationDataPayload {
    notes = notes == null ? List.of() : List.copyOf(notes);
  }

  /**
   * Creates an application-data payload from the details parsed from an application command.
   *
   * @param details the parsed application creation details
   * @return the sensitive data payload to persist
   */
  public static ApplicationDataPayload from(ApplicationCreationDetails details) {
    return new ApplicationDataPayload(
        details.laaReference(),
        details.applicationContent(),
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
        null,
        List.of());
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
        assignmentEventDescription,
        notes);
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
        newAssignmentEventDescription,
        notes);
  }

  /** Returns a complete new data version with the given note appended. */
  public ApplicationDataPayload withNote(String noteText, Instant createdAt) {
    List<ApplicationNote> updated = new ArrayList<>(notes);
    updated.add(new ApplicationNote(noteText, createdAt));
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
        assignmentEventDescription,
        List.copyOf(updated));
  }
}
