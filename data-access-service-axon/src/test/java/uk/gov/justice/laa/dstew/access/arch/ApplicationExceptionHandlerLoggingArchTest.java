package uk.gov.justice.laa.dstew.access.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

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
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Enforces the Sentry reporting rule documented in {@code docs/observability-sentry-policy.md}:
 * every {@code ApplicationExceptionHandler} method must explicitly log the outcome at {@code WARN}
 * or {@code ERROR} rather than relying solely on upstream aspect classification, so a handler added
 * without a deliberate severity choice fails this test instead of silently reaching Sentry with the
 * wrong (or no) severity.
 */
class ApplicationExceptionHandlerLoggingArchTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access.controller");

  private static final Set<String> LOG_METHOD_NAMES = Set.of("warn", "error");

  @Test
  void exceptionHandlerMethodsMustExplicitlyLogAtWarnOrError() {
    ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> logAtWarnOrError =
        new ArchCondition<>("log at WARN or ERROR") {
          @Override
          public void check(
              com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
            boolean logsAtExpectedSeverity =
                method.getMethodCallsFromSelf().stream()
                    .map(JavaMethodCall::getTarget)
                    .anyMatch(
                        target ->
                            isSlf4jLogger(target.getOwner())
                                && LOG_METHOD_NAMES.contains(target.getName()));

            if (!logsAtExpectedSeverity) {
              events.add(
                  SimpleConditionEvent.violated(
                      method,
                      String.format(
                          "Method <%s> does not call log.warn(...) or log.error(...); every"
                              + " exception-handler method must explicitly choose a Sentry"
                              + " reporting severity",
                          method.getFullName())));
            }
          }
        };

    ArchRule rule =
        methods()
            .that()
            .areAnnotatedWith(ExceptionHandler.class)
            .and()
            .areDeclaredInClassesThat()
            .haveSimpleName("ApplicationExceptionHandler")
            .should(logAtWarnOrError);
    rule.check(classes);
  }

  private static boolean isSlf4jLogger(JavaClass javaClass) {
    return javaClass.isAssignableTo(org.slf4j.Logger.class)
        || javaClass.getFullName().equals("org.slf4j.Logger");
  }
}
