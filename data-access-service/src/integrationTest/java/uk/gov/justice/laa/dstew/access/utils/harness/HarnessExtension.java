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
            // Method-level: enabled if the method itself OR the class carries @SmokeTest
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

        // Class-level: only disable the whole class if neither the class nor any of its
        // methods carry @SmokeTest — otherwise let method-level evaluation decide per test.
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
        var mode = System.getProperty("test.mode", "integration");
        TestContextProvider provider = INFRASTRUCTURE_MODE.equals(mode)
                ? new InfrastructureTestContextProvider()
                : new IntegrationTestContextProvider();
        getStore(ctx).put(STORE_KEY, provider);
    }

    @Override
    public void afterAll(ExtensionContext ctx) throws Exception {
        var provider = getStore(ctx).remove(STORE_KEY, TestContextProvider.class);
        if (provider != null) provider.close();
    }

    @Override
    public void postProcessTestInstance(Object instance, ExtensionContext ctx) {
        var provider = getStore(ctx).get(STORE_KEY, TestContextProvider.class);
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

    private ExtensionContext.Store getStore(ExtensionContext ctx) {
        return ctx.getStore(ExtensionContext.Namespace.create(
                HarnessExtension.class, ctx.getRequiredTestClass()));
    }
}
