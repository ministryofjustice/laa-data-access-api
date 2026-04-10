package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.reactive.server.WebTestClient;

public class HarnessExtension
    implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor {

  private static final Logger log = LoggerFactory.getLogger(HarnessExtension.class);

  private static final String STORE_KEY = "testContextProvider";
  private static final String ROW_COUNT_SNAPSHOT_KEY = "rowCountSnapshot";

  @Override
  public void beforeAll(ExtensionContext ctx) {
    log.info("[HarnessExtension] beforeAll: {}", ctx.getDisplayName());

    var provider = getOrCreateProvider(ctx);

    if (HarnessMode.isInfrastructure()) {
      var store = getRootStore(ctx);

      store.getOrComputeIfAbsent(
          ROW_COUNT_SNAPSHOT_KEY,
          key -> {
            log.info("[HarnessExtension] Capturing before-suite row-count snapshot");
            var snapshot = provider.getBean(TableRowCountAssertion.class).captureRowCounts();
            log.info("[HarnessExtension] Snapshot captured: {}", snapshot);
            return snapshot;
          });

      store.getOrComputeIfAbsent(
          ROW_COUNT_SNAPSHOT_KEY + ".asserter",
          key -> {
            log.info("[HarnessExtension] Registering end-of-suite row-count parity asserter");
            return (ExtensionContext.Store.CloseableResource)
                () -> {
                  log.info(
                      "[HarnessExtension] End-of-suite row-count parity asserter closing — running assertion");
                  @SuppressWarnings("unchecked")
                  var snapshot = (java.util.Map<String, Long>) store.get(ROW_COUNT_SNAPSHOT_KEY);
                  if (snapshot != null) {
                    provider
                        .getBean(TableRowCountAssertion.class)
                        .assertRowCountsMatch(snapshot, "full-infrastructure-suite");
                    log.info("[HarnessExtension] Row-count parity assertion PASSED");
                  } else {
                    log.warn(
                        "[HarnessExtension] Row-count snapshot was null — parity assertion skipped");
                  }
                };
          });
    }
  }

  @Override
  public void afterAll(ExtensionContext ctx) {
    log.info("[HarnessExtension] afterAll: {}", ctx.getDisplayName());
    // No-op: the provider is held in the root store and will be closed by
    // JUnit when the root context itself is torn down at the end of the suite.
    // The row-count parity assertion fires via the CloseableResource asserter
    // registered in beforeAll, which JUnit closes (in reverse registration order)
    // before the TestContextProvider — so the Spring context is still live.
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
            var value =
                WebTestClient.class.equals(field.getType())
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
    return getRootStore(ctx)
        .getOrComputeIfAbsent(
            STORE_KEY,
            key -> {
              log.info(
                  "[HarnessExtension] Creating TestContextProvider (mode={})",
                  System.getProperty(HarnessMode.PROPERTY, "integration (default)"));
              return HarnessMode.isInfrastructure()
                  ? new InfrastructureTestContextProvider()
                  : new IntegrationTestContextProvider();
            },
            TestContextProvider.class);
  }

  private ExtensionContext.Store getRootStore(ExtensionContext ctx) {
    return ctx.getRoot().getStore(ExtensionContext.Namespace.create(HarnessExtension.class));
  }
}
