package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.springframework.test.web.reactive.server.WebTestClient;

public class HarnessExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {

    private static final String STORE_KEY = "testContextProvider";
    private static final String INFRASTRUCTURE_MODE = "infrastructure";


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
