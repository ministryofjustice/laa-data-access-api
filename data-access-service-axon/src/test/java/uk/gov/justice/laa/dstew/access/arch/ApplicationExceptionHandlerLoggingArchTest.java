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
 * every {@code ApplicationExceptionHandler} method must explicitly choose its reporting severity -
 * either by logging at {@code WARN} (client-caused, breadcrumb-only) or {@code ERROR}, or by
 * delegating to {@link uk.gov.justice.laa.dstew.access.observability.ErrorReporter} (unexpected,
 * Sentry event) - rather than relying solely on upstream aspect classification. A handful of
 * handlers are deliberately exempt: {@code handleAggregateNotFoundException} logs unconditionally
 * at WARN with no exception argument (Axon's exception has no usable message), and {@code
 * handleHttpMessageNotReadableException} deliberately neither logs nor reports because malformed
 * request bodies are an expected, high-volume client error that would otherwise be noisy even as a
 * breadcrumb.
 */
class ApplicationExceptionHandlerLoggingArchTest {

  private static final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("uk.gov.justice.laa.dstew.access.controller");

  private static final Set<String> LOG_METHOD_NAMES = Set.of("warn", "error");

  private static final Set<String> EXEMPT_METHOD_NAMES =
      Set.of("handleAggregateNotFoundException", "handleHttpMessageNotReadableException");

  @Test
  void exceptionHandlerMethodsMustExplicitlyChooseReportingSeverity() {
    ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> logOrReport =
        new ArchCondition<>("log at WARN/ERROR or report via ErrorReporter") {
          @Override
          public void check(
              com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
            if (EXEMPT_METHOD_NAMES.contains(method.getName())) {
              return;
            }
            boolean choosesSeverity =
                method.getMethodCallsFromSelf().stream()
                    .map(JavaMethodCall::getTarget)
                    .anyMatch(
                        target ->
                            (isSlf4jLogger(target.getOwner())
                                    && LOG_METHOD_NAMES.contains(target.getName()))
                                || (target.getOwner().getSimpleName().equals("ErrorReporter")
                                    && target.getName().equals("report")));

            if (!choosesSeverity) {
              events.add(
                  SimpleConditionEvent.violated(
                      method,
                      String.format(
                          "Method <%s> does not call log.warn(...), log.error(...), or"
                              + " ErrorReporter.report(...); every exception-handler method must"
                              + " explicitly choose a Sentry reporting severity",
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
            .should(logOrReport);
    rule.check(classes);
  }

  private static boolean isSlf4jLogger(JavaClass javaClass) {
    return javaClass.isAssignableTo(org.slf4j.Logger.class)
        || javaClass.getFullName().equals("org.slf4j.Logger");
  }
}
