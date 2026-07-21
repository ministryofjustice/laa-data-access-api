package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;

class AxonArchitectureTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access");

  @Test
  void eventsMustNotDeclareEventTypedFields() {
    ArchCondition<JavaClass> declareEventTypedFields =
        new ArchCondition<>("declare fields whose type ends with Event") {
          @Override
          public void check(JavaClass javaClass, ConditionEvents events) {
            javaClass.getFields().stream()
                .filter(field -> field.getRawType().getSimpleName().endsWith("Event"))
                .forEach(
                    field ->
                        events.add(
                            SimpleConditionEvent.satisfied(
                                javaClass,
                                String.format(
                                    "Field <%s> has event type <%s>",
                                    field.getFullName(), field.getRawType().getName()))));
          }
        };

    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Event")
            .should(declareEventTypedFields)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void queryClassesMustNotDependOnCommandGateway() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..query..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CommandGateway.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void aggregatesMustNotDependOnQueryGateway() {
    ArchRule rule =
        noClasses()
            .that()
            .areAnnotatedWith(EventSourced.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(QueryGateway.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void commandAndEventMessagesInCommandPackagesMustBeRecords() {
    DescribedPredicate<JavaClass> commandOrEvent =
        new DescribedPredicate<>("have a simple name ending with Command or Event") {
          @Override
          public boolean test(JavaClass javaClass) {
            return javaClass.getSimpleName().endsWith("Command")
                || javaClass.getSimpleName().endsWith("Event");
          }
        };

    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..command..")
            .and(commandOrEvent)
            .should()
            .beRecords()
            .allowEmptyShould(true);
    rule.check(classes);
  }
}
