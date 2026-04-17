package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
@AnalyzeClasses(packages = "uk.gov.justice.laa.dstew.access")
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
}
