package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

final class ApplicationCommandHandlerSupport {

  private ApplicationCommandHandlerSupport() {}

  static void requireApplicationExists(
      ApplicationAggregate application, UUID requestedApplicationId) {
    if (application.getApplicationId() == null) {
      throw new ResourceNotFoundException(
          "No application found with Application ID: " + requestedApplicationId);
    }
  }

  static void requireApplicationDraft(ApplicationAggregate application) {
    if (application.getState().getStatus() != null) {
      throw new ValidationException(
          List.of("Documents can only be uploaded to an application draft"));
    }
  }

  /**
   * Returns the open draft's content, or throws not-found if there is no open draft. A draft whose
   * content has been erased is treated as missing.
   */
  static ApplicationDraftContent requireDraft(
      ApplicationAggregate application, UUID requestedApplicationId) {
    return findDraft(application)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No application draft found with Application ID: " + requestedApplicationId));
  }

  /**
   * Returns the open draft's content: empty once the Application is submitted, if it does not
   * exist, or if its draft content has been erased. This is the one place outside evolve functions
   * and registered projections where Application content is read.
   */
  static Optional<ApplicationDraftContent> findDraft(ApplicationAggregate application) {
    if (application == null || application.getApplicationId() == null) {
      return Optional.empty();
    }
    ApplicationState state = application.getState();
    if (state.getStatus() != null
        || state.getDraftApplicationContent().isErased()
        || state.getDraftSerialisedRequest().isErased()) {
      return Optional.empty();
    }
    Map<UUID, String> filenames = new LinkedHashMap<>();
    state
        .getDraftDocumentFilenames()
        .forEach(
            (documentId, filename) -> {
              if (!filename.isErased()) {
                filenames.put(documentId, filename.value());
              }
            });
    return Optional.of(
        new ApplicationDraftContent(
            state.getDraftStatus(),
            state.getLaaReference(),
            state.getDraftApplicationContent().value(),
            state.getDraftSerialisedRequest().value(),
            state.getDraftPotentialDuplicates().orElse(null),
            filenames));
  }

  /**
   * Returns the Application's current submitted content, folded from its events. Used only to keep
   * writing {@code application_data} until the readers move to events.
   *
   * @throws IllegalStateException if the content has been erased
   */
  static ApplicationDataPayload currentContent(ApplicationAggregate application) {
    var content = application.getState().getContent();
    if (content.isErased()) {
      throw new IllegalStateException(
          "Application content has been erased: " + application.getApplicationId());
    }
    return content.value();
  }

  @SuppressWarnings("unchecked")
  static String officeCode(Map<String, Object> applicationContent) {
    Object provider = applicationContent.get("provider");
    if (!(provider instanceof Map<?, ?> providerMap)) {
      return null;
    }
    Object officeCode = ((Map<String, Object>) providerMap).get("officeCode");
    return officeCode instanceof String code ? code : null;
  }
}
