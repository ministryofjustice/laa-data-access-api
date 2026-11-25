package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.EventTypes;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.eventcontent.ReadsEventContent;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Rules that keep event content readable only where erasure has been accounted for. */
class EventContentArchitectureTest {

  private static final String ROOT = "uk.gov.justice.laa.dstew.access";

  /** Retained fields that may be null on append, by event record. Every other one is required. */
  private static final Map<String, Set<String>> NULLABLE_RETAINED_FIELDS =
      Map.ofEntries(
          entry("ApplicationCreatedEvent", Set.of("requestFingerprint", "status", "officeCode")),
          entry(
              "ApplicationDecisionMadeEvent",
              Set.of("overallDecision", "autoGranted", "decisionRequestFingerprint")),
          entry("ApplicationDocumentUploadedEvent", Set.of("checksum", "applicationDataVersion")),
          entry(
              "ApplicationDraftStartedEvent",
              Set.of("requestFingerprint", "officeCode", "status", "laaReference")),
          entry("ApplicationUpdatedEvent", Set.of("previousStatus", "status")),
          entry(
              "LinkedApplicationGroupCreatedEvent",
              Set.of("memberApplicationIds", "justificationId")),
          entry(
              "LinkedApplicationGroupDissolvedEvent",
              Set.of("memberApplicationIds", "justificationId")),
          entry(
              "LinkedApplicationGroupLeadChangedEvent",
              Set.of("memberApplicationIds", "justificationId")),
          entry("MemberAddedToGroupEvent", Set.of("memberApplicationIds", "justificationId")),
          entry("MemberRemovedFromGroupEvent", Set.of("memberApplicationIds", "justificationId")),
          entry("PriorAuthorityDecisionMadeEvent", Set.of("priorAuthorityType", "overallDecision")),
          entry("PriorAuthorityDocumentUploadedEvent", Set.of("checksum")),
          entry("PriorAuthorityDraftStartedEvent", Set.of("priorAuthorityType", "officeCode")),
          entry(
              "PriorAuthoritySubmittedEvent",
              Set.of("priorAuthorityType", "applicationDataVersion", "billingType")),
          entry("WorkItemAssigned", Set.of("workItemType", "caseworkerId")),
          entry("WorkItemUnassigned", Set.of("workItemType")));

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          // Shared test helpers, such as the in-memory content store, are not production code.
          .withImportOption(location -> !location.contains("/testUtilities/"))
          .importPackages(ROOT);

  /**
   * Events carry content as plain nullable values; aggregate state holds it as {@link Erasable}.
   * Only the command side may unwrap it: evolve functions, whose erasure is covered by
   * ErasureReplayTest, aggregate state, deciders and command handlers, which treat erased content
   * explicitly. Projections read plain event fields and never see an {@link Erasable}.
   */
  @Test
  void onlyEvolveStateDeciderAndCommandHandlersReadErasable() {
    noClasses()
        .that()
        .resideOutsideOfPackage(ROOT + ".eventcontent..")
        .and()
        .haveSimpleNameNotEndingWith("Evolve")
        .and()
        .haveSimpleNameNotEndingWith("State")
        .and()
        .haveSimpleNameNotEndingWith("Decider")
        .and()
        .haveSimpleNameNotEndingWith("CommandHandler")
        .and()
        .haveSimpleNameNotEndingWith("CommandHandlerSupport")
        .should()
        .callMethodWhere(
            target(owner(describe("Erasable", owner -> owner.isEquivalentTo(Erasable.class))))
                .and(target(nameMatching("value|orElse|map|isErased"))))
        .because(
            "only aggregate state and the command side may unwrap content, which may be erased")
        .check(classes);
  }

  @Test
  void onlyEventContentAndProcessorConfigTouchContentStorage() {
    noClasses()
        .that()
        .resideOutsideOfPackages(ROOT + ".eventcontent..", ROOT + ".config..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ROOT + ".eventcontent.axon..", ROOT + ".eventcontent.jdbc..")
        .orShould()
        .dependOnClassesThat()
        .haveSimpleName("EventContentStore")
        .check(classes);
  }

  @Test
  void aggregatesMustNotSnapshot() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveSimpleNameContaining("Snapshot")
        .because("a snapshot would keep content after the event's content row is erased")
        .check(classes);
  }

  @Test
  void givenContentReadingProjection_whenImported_thenItNamesItsProcessor() {
    classes()
        .that()
        .areAnnotatedWith(ReadsEventContent.class)
        .should()
        .beAnnotatedWith(Namespace.class)
        .because("the processor that receives content is named by the projection's @Namespace")
        .check(classes);
  }

  @Test
  void givenEventClasses_whenScanned_thenEachIsRecordNamedInAccess() {
    var events =
        classes.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(Event.class))
            .<Class<?>>map(javaClass -> javaClass.reflect())
            .toList();
    assertThat(events).isNotEmpty();
    // The separator keeps fields only for the event classes it is given.
    assertThat(EventTypes.all().values()).containsExactlyInAnyOrderElementsOf(events);

    for (Class<?> event : events) {
      Event annotation = event.getAnnotation(Event.class);
      assertThat(annotation.namespace()).as(event.getName()).isEqualTo("access");
      assertThat(annotation.name()).as(event.getName()).isNotBlank();
      assertThat(event.isRecord()).as(event.getName() + " is a record").isTrue();
    }
  }

  @Test
  void givenRetainedFields_whenInspected_thenEachHasSupportedType() {
    var unsupported =
        retainedComponents()
            .filter(component -> !isSupported(component.getGenericType()))
            .map(EventContentArchitectureTest::nameOf)
            .toList();

    assertThat(unsupported).as("retained fields with an unsupported type").isEmpty();
  }

  @Test
  void givenRetainedFields_whenInspected_thenNoPrimitiveIsMarkedNullable() {
    var primitives =
        retainedComponents()
            .filter(component -> component.getType().isPrimitive())
            .filter(component -> component.getAnnotation(RetainedField.class).nullable())
            .map(EventContentArchitectureTest::nameOf)
            .toList();

    assertThat(primitives).as("primitive retained fields marked nullable").isEmpty();
  }

  @Test
  void givenRetainedFields_whenInspected_thenOnlyListedFieldsAreNullable() {
    var nullable =
        retainedComponents()
            .filter(component -> component.getAnnotation(RetainedField.class).nullable())
            .collect(
                Collectors.groupingBy(
                    component -> component.getDeclaringRecord().getSimpleName(),
                    Collectors.mapping(RecordComponent::getName, Collectors.toSet())));

    assertThat(nullable).isEqualTo(NULLABLE_RETAINED_FIELDS);
  }

  @Test
  void givenEventComponents_whenInspected_thenNoneIsErasable() {
    var erasable =
        eventComponents()
            .filter(component -> component.getType() == Erasable.class)
            .map(EventContentArchitectureTest::nameOf)
            .toList();

    assertThat(erasable).as("event components declared Erasable").isEmpty();
  }

  private static Stream<RecordComponent> eventComponents() {
    return EventTypes.all().values().stream()
        .filter(Class::isRecord)
        .flatMap(event -> Arrays.stream(event.getRecordComponents()));
  }

  private static Stream<RecordComponent> retainedComponents() {
    return eventComponents()
        .filter(component -> component.isAnnotationPresent(RetainedField.class));
  }

  /**
   * Retained fields are kept forever, so they hold only identifiers, enums, numbers, booleans,
   * times, strings, and collections or maps of those.
   */
  private static boolean isSupported(Type type) {
    if (type instanceof ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> container
        && (Collection.class.isAssignableFrom(container)
            || Map.class.isAssignableFrom(container))) {
      return Arrays.stream(parameterized.getActualTypeArguments())
          .allMatch(EventContentArchitectureTest::isSupported);
    }
    return type instanceof Class<?> simple
        && (simple.isPrimitive()
            || simple.isEnum()
            || simple == UUID.class
            || simple == String.class
            || simple == Boolean.class
            || Number.class.isAssignableFrom(simple)
            || "java.time".equals(simple.getPackageName()));
  }

  private static String nameOf(RecordComponent component) {
    return component.getDeclaringRecord().getSimpleName()
        + "."
        + component.getName()
        + " "
        + component.getGenericType().getTypeName();
  }
}
