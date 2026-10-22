package uk.gov.justice.laa.dstew.access.pact;

import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.DeletePriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerFactory;

/**
 * Runs on every build without the broker (Gradle task {@code pactBootCheck}, wired into {@code
 * check}). It boots the Pact provider context on in-memory H2 and exercises every provider state,
 * so a renamed use case, a broken profile, or a seeding helper that no longer satisfies the domain
 * rules fails the pull request rather than the next consumer-triggered verification.
 *
 * <p>It does not replay any consumer contract. That is what {@link DataAccessApiProviderTests}
 * does, with the broker.
 */
class PactProviderBootCheckTest extends AbstractProviderPactTests {

  @Autowired private QueryGateway queryGateway;
  @Autowired private DeletePriorAuthorityDocumentUseCase deleteDocumentUseCase;
  @Autowired private AuthenticatedCallerFactory authenticatedCallerFactory;

  @Test
  void everyApplicationStateCanBeSeeded() {
    states.applicationsExist();
    states.clientIndividualExistsForApplication001();
    states.noMatchingSpecialChildrenActApplicationExists();
    states.submittedApplicationExists();
    states.applicationAssignedExists();
    states.applicationUnassignedExists();
    states.applicationGrantedExists();
    states.applicationWithNotesExists();
    states.applicationsLinked();
    states.noApplicationWithId();
    states.workListItemsExist();
  }

  @Test
  void everyPriorAuthorityStateCanBeSeeded() {
    states.priorAuthorityDraftExists();
    states.priorAuthoritySubmittedExists();
    states.priorAuthorityAssignedExists();
    states.priorAuthorityDecidedExists();
    Map<String, Object> injected = states.priorAuthorityWithDocumentExists();
    assertThat(injected).containsKey("documentId");
    states.noPriorAuthorityWithId();
    states.applicationWithPriorAuthorities();
    assertThat(states.newPriorAuthorityDraftExists())
        .containsKeys("priorAuthorityId", "applicationId");
    assertThat(states.newPriorAuthorityDraftWithDocumentExists())
        .containsKeys("priorAuthorityId", "applicationId", "documentId");
  }

  @Test
  void everyStateIsIdempotent() {
    // A state may run several times in one verification run; the second pass must be a no-op.
    states.applicationGrantedExists();
    states.applicationGrantedExists();
    states.priorAuthorityDecidedExists();
    states.priorAuthorityDecidedExists();
    assertThat(states.priorAuthorityWithDocumentExists())
        .isEqualTo(states.priorAuthorityWithDocumentExists());
  }

  @Test
  void freshPriorAuthorityStatesCreateAnotherDraftEveryTime() {
    // Consumers submit, update and upload against these, so no two interactions may share one.
    Map<String, Object> first = states.newPriorAuthorityDraftExists();
    Map<String, Object> second = states.newPriorAuthorityDraftExists();
    Map<String, Object> withDocument = states.newPriorAuthorityDraftWithDocumentExists();

    assertThat(first.get("priorAuthorityId")).isNotEqualTo(second.get("priorAuthorityId"));
    assertThat(withDocument.get("priorAuthorityId")).isNotEqualTo(second.get("priorAuthorityId"));
    assertThat(first.get("applicationId")).isEqualTo(PactIds.APPLICATION_PA_PARENT.toString());
    assertThat(priorAuthority(second).status()).isEqualTo(PriorAuthorityStateSeeder.DRAFT_STATUS);
    assertThat(priorAuthority(withDocument).uploadedDocuments())
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document.documentId().toString())
                  .isEqualTo(withDocument.get("documentId"));
              assertThat(document.deleted()).isFalse();
            });
  }

  @Test
  void documentStateHandsOutAnActiveDocumentAfterDeletion() {
    // A consumer that deletes a005's document must not leave the state injecting a dead ID.
    UUID priorAuthorityId = PactIds.PRIOR_AUTHORITY_WITH_DOCUMENT;
    UUID before =
        UUID.fromString((String) states.priorAuthorityWithDocumentExists().get("documentId"));

    deleteDocumentUseCase.execute(
        priorAuthorityId, before, authenticatedCallerFactory.currentCaller());
    Awaitility.await()
        .atMost(ApplicationStateSeeder.PROJECTION_WAIT)
        .pollInterval(ApplicationStateSeeder.POLL_INTERVAL)
        .until(
            () ->
                find(priorAuthorityId).uploadedDocuments().stream()
                    .anyMatch(
                        document -> before.equals(document.documentId()) && document.deleted()));

    UUID after =
        UUID.fromString((String) states.priorAuthorityWithDocumentExists().get("documentId"));
    assertThat(after).isNotEqualTo(before);
    assertThat(find(priorAuthorityId).uploadedDocuments())
        .anySatisfy(
            document -> {
              assertThat(document.documentId()).isEqualTo(after);
              assertThat(document.deleted()).isFalse();
            });
  }

  private PriorAuthorityResult priorAuthority(Map<String, Object> injected) {
    return find(UUID.fromString((String) injected.get("priorAuthorityId")));
  }

  private PriorAuthorityResult find(UUID priorAuthorityId) {
    return queryGateway
        .query(
            new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
            PriorAuthorityResult.class)
        .join();
  }

  @Test
  void laggingReadModelStateRestoresTheProjection() {
    states.applicationReadModelLagging();
    states.applicationReadModelCaughtUp();
    // Projection is back: a fresh state can still be seeded afterwards.
    states.submittedApplicationExists();
  }

  @Test
  void everyCatalogueStateHasHandler() throws IllegalAccessException {
    Set<String> catalogue = catalogueStrings();
    Set<String> handled =
        Arrays.stream(DataAccessApiProviderTests.class.getMethods())
            .map(method -> method.getAnnotation(State.class))
            .filter(state -> state != null && state.action() == StateChangeAction.SETUP)
            .flatMap(state -> Arrays.stream(state.value()))
            .collect(Collectors.toSet());
    assertThat(handled).containsExactlyInAnyOrderElementsOf(catalogue);
  }

  private static Set<String> catalogueStrings() throws IllegalAccessException {
    List<Field> fields =
        Arrays.stream(PactStates.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> field.getType() == String.class)
            .toList();
    Set<String> strings = new java.util.HashSet<>();
    for (Field field : fields) {
      field.setAccessible(true);
      strings.add((String) field.get(null));
    }
    return strings;
  }
}
