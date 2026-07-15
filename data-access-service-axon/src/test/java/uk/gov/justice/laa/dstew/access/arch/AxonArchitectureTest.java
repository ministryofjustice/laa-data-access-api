package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.jupiter.api.Test;

class AxonArchitectureTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access");

  private static final Set<String> BLOCKING_GATEWAY_METHODS =
      Set.of("publish", "scatterGather", "sendAndWait");

  @Test
  void sagasMustNotCallBlockingGatewayMethods() {
    DescribedPredicate<JavaMethodCall> blockingGatewayCall =
        new DescribedPredicate<>("call a blocking Axon gateway method") {
          @Override
          public boolean test(JavaMethodCall methodCall) {
            String ownerName = methodCall.getTarget().getOwner().getName();
            return ownerName.startsWith("org.axonframework.")
                && ownerName.endsWith("Gateway")
                && BLOCKING_GATEWAY_METHODS.contains(methodCall.getTarget().getName());
          }
        };

    ArchRule rule =
        noClasses()
            .that()
            .areAnnotatedWith(Saga.class)
            .should()
            .callMethodWhere(blockingGatewayCall)
            .allowEmptyShould(true);
    rule.check(classes);
  }

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
            .areAnnotatedWith(Aggregate.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(QueryGateway.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  void commandMessagesMustBeRecords() {
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
