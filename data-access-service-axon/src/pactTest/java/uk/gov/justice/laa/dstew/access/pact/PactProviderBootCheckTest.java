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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

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
  }

  @Test
  void everyStateIsIdempotent() {
    // A state may run several times in one verification run; the second pass must be a no-op.
    states.applicationGrantedExists();
    states.applicationGrantedExists();
    states.priorAuthorityDecidedExists();
    states.priorAuthorityDecidedExists();
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
