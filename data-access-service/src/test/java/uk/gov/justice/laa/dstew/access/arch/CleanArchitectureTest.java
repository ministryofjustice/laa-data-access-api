package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class CleanArchitectureTest {

  private static final JavaClasses classes =
      new ClassFileImporter().importPackages("uk.gov.justice.laa.dstew.access");

  @Test
  void useCasesMustNotImportApiModelApplicationCreateRequest() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..model..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void useCasesMustNotImportJpaEntities() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..entity..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void useCasesMustNotImportRepositories() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void domainMustNotImportFrameworkOrEntityTypes() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..entity..", "..repository..", "jakarta.persistence..", "org.springframework..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void jpaGatewaysMustNotBeAnnotatedWithComponent() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa..")
            .should()
            .beAnnotatedWith(org.springframework.stereotype.Component.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void jpaGatewaysMustNotBeAnnotatedWithTransactional() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa..")
            .should()
            .beAnnotatedWith(jakarta.transaction.Transactional.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getApplicationUseCaseMustNotImportApiModels() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase.getapplication..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..model..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getApplicationUseCaseMustNotImportJpaEntities() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase.getapplication..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..entity..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getApplicationJpaGatewayMustNotBeAnnotatedWithComponent() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa.getapplication..")
            .should()
            .beAnnotatedWith(org.springframework.stereotype.Component.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getApplicationJpaGatewayMustNotBeAnnotatedWithTransactional() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa.getapplication..")
            .should()
            .beAnnotatedWith(jakarta.transaction.Transactional.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }
}
