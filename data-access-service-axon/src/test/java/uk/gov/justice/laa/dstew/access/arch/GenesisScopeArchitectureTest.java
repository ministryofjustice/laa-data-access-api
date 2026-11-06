package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;

/** Every event names the root applications it relates to, so history can find it statelessly. */
class GenesisScopeArchitectureTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access");

  @Test
  void everyEventIsGenesisScoped() {
    classes()
        .that()
        .areAnnotatedWith(Event.class)
        .should()
        .implement(GenesisScoped.class)
        .because("history finds an application's events by their genesis IDs")
        .check(classes);
  }

  @Test
  void givenDissolvedGroup_whenScoped_thenIncludesEveryAffectedApplicationOnce() {
    UUID lead = UUID.randomUUID();
    UUID removed = UUID.randomUUID();
    UUID remaining = UUID.randomUUID();

    LinkedApplicationGroupDissolvedEvent event =
        new LinkedApplicationGroupDissolvedEvent(
            UUID.randomUUID(), lead, removed, List.of(lead, remaining), 2L, Instant.now());

    assertThat(event.genesisIds()).containsExactlyInAnyOrder(lead, removed, remaining);
  }

  @Test
  void givenMissingApplicationId_whenScoped_thenFails() {
    LinkedApplicationGroupLeadChangedEvent event =
        new LinkedApplicationGroupLeadChangedEvent(
            UUID.randomUUID(), null, UUID.randomUUID(), 1L, Instant.now());

    assertThatNullPointerException().isThrownBy(event::genesisIds);
  }
}
