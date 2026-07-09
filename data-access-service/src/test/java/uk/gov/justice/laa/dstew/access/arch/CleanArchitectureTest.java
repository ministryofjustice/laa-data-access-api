package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class CleanArchitectureTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access");

  @ArchIgnore(
      reason = "Disabled until we can refactor the use cases to not depend on the API model")
  @Test
  void useCasesMustNotImportApiModelApplicationCreateRequest() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("uk.gov.justice.laa.dstew.access.model")
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
            .resideInAPackage("uk.gov.justice.laa.dstew.access.model")
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

  @Test
  void domainClassesMustFollowReadModelOrDomainNamingConvention() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("uk.gov.justice.laa.dstew.access.domain")
            .and()
            .areNotAnonymousClasses()
            .and()
            .areNotMemberClasses()
            .should()
            .haveSimpleNameEndingWith("Domain")
            .orShould()
            .haveSimpleNameEndingWith("ReadModel")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void readModelTypesMustNotBeImportedByWritePathUseCases() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase..")
            .and()
            .resideOutsideOfPackage(
                "..usecase.getapplication..") // read-path; legitimately uses ReadModels
            .and()
            .areNotMemberClasses() // exclude Lombok-generated builder inner classes
            .should()
            .dependOnClassesThat()
            .resideInAPackage(
                "..usecase.getapplication.model..") // guard against getapplication ReadModel
            // leakage only
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void dbProjectionTypesMustResideInDtoPackage() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("DbProjection")
            .should()
            .resideInAPackage("..usecase..dto..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void dbProjectionTypesMustNotImportFrameworkOrEntityTypes() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("DbProjection")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..entity..", "..repository..", "jakarta.persistence..", "org.springframework..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void readModelTypesMustNotDependOnDbProjections() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("ReadModel")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("DbProjection")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void controllerMustNotImportDbProjections() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("DbProjection")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getCertificateUseCaseMustNotImportApiModels() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase.getcertificate..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("uk.gov.justice.laa.dstew.access.model")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getCertificateUseCaseMustNotImportJpaEntities() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..usecase.getcertificate..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..entity..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getCertificateJpaGatewayMustNotBeAnnotatedWithComponent() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa.getcertificate..")
            .should()
            .beAnnotatedWith(org.springframework.stereotype.Component.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void getCertificateJpaGatewayMustNotBeAnnotatedWithTransactional() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.jpa.getcertificate..")
            .should()
            .beAnnotatedWith(jakarta.transaction.Transactional.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }
}
