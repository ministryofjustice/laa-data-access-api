package uk.gov.justice.laa.dstew.access.replay;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModelEvolve;

/**
 * Reconstructs an {@link ApplicationReadModel} directly from the raw Axon event store, bypassing
 * the Axon query API entirely. Events are read via plain JDBC, deserialised with Jackson, and
 * folded via {@link ApplicationReadModelEvolve}; the resulting persisted-column state is then
 * hydrated with its sensitive {@code application_data} payload, mirroring {@code
 * ApplicationProjection}'s query-handling logic.
 *
 * <p>This exists as a diagnostic/recovery tool — e.g. to verify or rebuild a projection row without
 * relying on Axon's query infrastructure being available.
 */
@Service
public class ApplicationRawReplayService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ApplicationDataStore applicationDataStore;

  /** Constructs the service with its raw-JDBC, serialisation, and data-store dependencies. */
  public ApplicationRawReplayService(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      ApplicationDataStore applicationDataStore) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.applicationDataStore = applicationDataStore;
  }

  /**
   * Replays the event stream for the given Application and returns the reconstructed, data-hydrated
   * read model, or {@link Optional#empty()} if no events are stored for it.
   */
  public Optional<ApplicationReadModel> replay(UUID applicationId) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT payload, payload_type FROM axon.domain_event_entry"
                + " WHERE aggregate_identifier = ? ORDER BY sequence_number",
            applicationId.toString());
    if (rows.isEmpty()) {
      return Optional.empty();
    }

    ApplicationReadModel model = new ApplicationReadModel();
    try {
      RawEventReplayer.replay(rows, model, dispatchers());
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Unable to replay raw event stream for application " + applicationId, exception);
    }

    ApplicationDataPayload data =
        applicationDataStore.get(applicationId, model.getApplicationDataVersion());
    return Optional.of(hydrate(model, data));
  }

  private Map<String, RawEventReplayer.EventApplier<ApplicationReadModel>> dispatchers() {
    return Map.of(
        "uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, ApplicationCreatedEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, ApplicationDecisionMadeEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, ApplicationAssignedToCaseworkerEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m,
                    objectMapper.readValue(
                        payload, ApplicationUnassignedFromCaseworkerEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, NoteCreatedEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, ApplicationLinkedEvent.class)),
        "uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested",
            (m, payload) ->
                ApplicationReadModelEvolve.apply(
                    m, objectMapper.readValue(payload, LinkedApplicationGroupRequested.class)));
  }

  private ApplicationReadModel hydrate(ApplicationReadModel model, ApplicationDataPayload data) {
    model.setLaaReference(data.laaReference());
    model.setApplicationContent(data.applicationContent());
    model.setIndividuals(data.individuals());
    model.setSubmittedAt(data.submittedAt());
    model.setOfficeCode(data.officeCode());
    model.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    model.setCategoryOfLaw(data.categoryOfLaw() == null ? null : data.categoryOfLaw().name());
    model.setMatterType(data.matterType() == null ? null : data.matterType().name());
    model.setProceedings(data.proceedings());
    model.setDecisionStatus(data.overallDecision());
    model.setAutoGranted(data.autoGranted());
    model.setMeritsDecisions(data.meritsDecisions());
    model.setCertificate(data.certificate());
    return model;
  }
}
