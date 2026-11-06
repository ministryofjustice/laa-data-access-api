package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.eventcontent.EventContentSeparator;
import uk.gov.justice.laa.dstew.access.eventcontent.EventJson;

/** Rules that keep event content readable only where erasure has been accounted for. */
class EventContentArchitectureTest {

  private static final String ROOT = "uk.gov.justice.laa.dstew.access";

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  /**
   * Content may be read only by evolve functions, whose erasure is covered by ErasureReplayTest,
   * and by the processors in RegisteredContentReaders. Subscribing processors receive events with
   * content attached, so this rule, not delivery, is what keeps them from reading it. The one
   * command-side reader is ApplicationCommandHandlerSupport, which reads draft content for submit
   * and treats erased draft content as no draft. ApplicationReadModelAssembler reads content from
   * the sourced application aggregate for single-application reads and treats erased content as not
   * found. ApplicationHistoryMapper reads note text and decision descriptions from events loaded
   * through StoredEvents under the application-history reader, and shows erased content as absent.
   */
  @Test
  void onlyEvolveAndRegisteredProjectionsReadContent() {
    noClasses()
        .that()
        .resideOutsideOfPackage(ROOT + ".eventcontent..")
        .and()
        .haveSimpleNameNotEndingWith("Evolve")
        .and()
        .doNotHaveFullyQualifiedName(ROOT + ".query.application.ApplicationProjection")
        .and()
        .doNotHaveFullyQualifiedName(ROOT + ".query.application.ApplicationReadModelAssembler")
        .and()
        .doNotHaveFullyQualifiedName(
            ROOT + ".query.application.priorauthority.PriorAuthorityProjection")
        .and()
        .doNotHaveFullyQualifiedName(
            ROOT + ".command.application.handler.ApplicationCommandHandlerSupport")
        .and()
        .doNotHaveFullyQualifiedName(ROOT + ".query.application.history.ApplicationHistoryMapper")
        .should()
        .callMethodWhere(
            target(owner(describe("Erasable", owner -> owner.isEquivalentTo(Erasable.class))))
                .and(target(nameMatching("value|orElse|map|isErased"))))
        .because("only registered readers may see content, and content may be erased")
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
  void everyEventIsNamedInAccessAndItsSchemaListsExactlyTheKeptFields() throws IOException {
    var events =
        classes.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(Event.class))
            .<Class<?>>map(javaClass -> javaClass.reflect())
            .toList();
    assertThat(events).isNotEmpty();

    EventJson json = new EventJson();
    for (Class<?> event : events) {
      Event annotation = event.getAnnotation(Event.class);
      assertThat(annotation.namespace()).as(event.getName()).isEqualTo("access");
      assertThat(annotation.name()).as(event.getName()).isNotBlank();
      assertThat(event.isRecord()).as(event.getName() + " is a record").isTrue();

      String typeName = annotation.namespace() + "." + annotation.name();
      Set<String> kept =
          Arrays.stream(event.getRecordComponents())
              .filter(component -> component.getType() != Erasable.class)
              .map(RecordComponent::getName)
              .collect(Collectors.toSet());

      JsonNode schema;
      try (InputStream in =
          getClass()
              .getClassLoader()
              .getResourceAsStream(EventContentSeparator.SCHEMA_ROOT + typeName + ".json")) {
        assertThat(in).as("schema for " + typeName).isNotNull();
        schema = json.mapper().readTree(in);
      }
      assertThat(schema.get("additionalProperties").asBoolean(true))
          .as(typeName + " additionalProperties")
          .isFalse();
      assertThat(new HashSet<>(schema.get("properties").propertyNames()))
          .as(typeName + " schema properties")
          .isEqualTo(kept);
    }
  }
}
