package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.lang.reflect.Method;
import java.util.Arrays;

public class HarnessExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor, ExecutionCondition {

    private static final String STORE_KEY = "testContextProvider";
    private static final String INFRASTRUCTURE_MODE = "infrastructure";

    // ── ExecutionCondition ────────────────────────────────────────────────────

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        if (!INFRASTRUCTURE_MODE.equals(System.getProperty("test.mode"))) {
            return ConditionEvaluationResult.enabled("Normal integration mode — all tests run");
        }

        boolean isMethodLevel = ctx.getTestMethod().isPresent();

        if (isMethodLevel) {
            boolean smokeMethod = ctx.getTestMethod()
                    .map(m -> m.isAnnotationPresent(SmokeTest.class))
                    .orElse(false);
            boolean smokeClass = ctx.getTestClass()
                    .map(c -> c.isAnnotationPresent(SmokeTest.class))
                    .orElse(false);

            if (smokeMethod || smokeClass) {
                return ConditionEvaluationResult.enabled("@SmokeTest — included in infrastructure mode");
            }
            return ConditionEvaluationResult.disabled("Not annotated with @SmokeTest — skipped in infrastructure mode");
        }

        boolean smokeClass = ctx.getTestClass()
                .map(c -> c.isAnnotationPresent(SmokeTest.class))
                .orElse(false);
        boolean anySmokeMethods = ctx.getTestClass()
                .map(c -> Arrays.stream(c.getMethods()).anyMatch(m -> m.isAnnotationPresent(SmokeTest.class)))
                .orElse(false);

        if (smokeClass || anySmokeMethods) {
            return ConditionEvaluationResult.enabled("Class contains @SmokeTest — evaluating per method");
        }
        return ConditionEvaluationResult.disabled("No @SmokeTest found on class or methods — skipped in infrastructure mode");
    }

    @Override
    public void beforeAll(ExtensionContext ctx) {
        // Use the root store so that a single TestContextProvider is shared across
        // all test classes in the suite, rather than creating a new Spring context
        // per class.
        getOrCreateProvider(ctx);
    }

    @Override
    public void afterAll(ExtensionContext ctx) {
        // No-op: the provider is held in the root store and will be closed by
        // JUnit when the root context itself is torn down at the end of the suite.
    }

    @Override
    public void postProcessTestInstance(Object instance, ExtensionContext ctx) {
        var provider = getOrCreateProvider(ctx);
        Class<?> clazz = instance.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (var field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(HarnessInject.class)) {
                    field.setAccessible(true);
                    try {
                        var value = WebTestClient.class.equals(field.getType())
                                ? provider.webTestClient()
                                : TestContextProvider.class.equals(field.getType())
                                        ? provider
                                        : provider.getBean(field.getType());
                        field.set(instance, value);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private TestContextProvider getOrCreateProvider(ExtensionContext ctx) {
        return getRootStore(ctx).getOrComputeIfAbsent(STORE_KEY, key -> {
            var mode = System.getProperty("test.mode", "integration");
            return INFRASTRUCTURE_MODE.equals(mode)
                    ? new InfrastructureTestContextProvider()
                    : new IntegrationTestContextProvider();
        }, TestContextProvider.class);
    }

    private ExtensionContext.Store getRootStore(ExtensionContext ctx) {
        return ctx.getRoot().getStore(ExtensionContext.Namespace.create(HarnessExtension.class));
    }
}
