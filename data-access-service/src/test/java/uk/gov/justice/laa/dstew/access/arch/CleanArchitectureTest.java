package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;

/**
 * Architecture tests enforcing clean architecture boundaries.
 *
 * <p>Excluded legacy packages (not yet migrated to clean architecture):
 *
 * <ul>
 *   <li>service/ — service classes call repositories directly (legacy pattern)
 *   <li>mapper/ — existing mappers depend on both entity and model types
 *   <li>validation/ — ApplicationValidations depends on model types
 *   <li>specification/ — Spring Data Specification helpers
 *   <li>transformation/ — AOP transformation pipeline
 *   <li>convertors/ — enum conversion utilities
 *   <li>model/ — internal model classes (non-generated); to be moved to domain/ over time
 * </ul>
 */
@AnalyzeClasses(packagesOf = AccessApp.class)
public class CleanArchitectureTest {

  @ArchTest
  static final ArchRule useCasesMustNotDependOnEntities =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .and()
          .haveSimpleNameNotEndingWith("Mapper")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..entity..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCasesMustNotDependOnRepositories =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCasesMustNotDependOnApiModels =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .and()
          .haveSimpleNameNotEndingWith("Mapper")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("uk.gov.justice.laa.dstew.access.model..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domainMustBeFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "..entity..",
              "..repository..",
              "..model..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule controllersMustNotDependOnRepositories =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCasesMustNotDependOnInfrastructureImplementations =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.jpa..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCasesMustNotDependOnSpringSecurityAnnotations =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.security..", "org.springframework.security.access.prepost..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domainObjectsMustBeRecordsOrEnums =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .areNotEnums()
          .should()
          .notBeRecords()
          .as("Domain objects must be records (immutable value types) — use a record, not a class")
          .allowEmptyShould(true);

  private static final String ID_RULE_REASON =
      "IDs are database-generated; use case code must not set them";

  @ArchTest
  static final ArchRule useCase_must_not_set_applicationDomain_id =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .callMethod(ApplicationDomain.ApplicationDomainBuilder.class, "id", UUID.class)
          .because(ID_RULE_REASON)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCase_must_not_set_decisionDomain_id =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .callMethod(DecisionDomain.DecisionDomainBuilder.class, "id", UUID.class)
          .because(ID_RULE_REASON)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule useCase_must_not_set_meritsDecisionDomain_id =
      noClasses()
          .that()
          .resideInAPackage("..usecase..")
          .should()
          .callMethod(MeritsDecisionDomain.MeritsDecisionDomainBuilder.class, "id", UUID.class)
          .because(ID_RULE_REASON)
          .allowEmptyShould(true);
}
