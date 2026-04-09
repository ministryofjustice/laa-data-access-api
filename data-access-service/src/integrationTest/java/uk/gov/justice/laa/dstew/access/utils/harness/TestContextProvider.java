package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.web.reactive.server.WebTestClient;

public interface TestContextProvider extends AutoCloseable, ExtensionContext.Store.CloseableResource {
    WebTestClient webTestClient();
    <T> T getBean(Class<T> type);
}
