package uk.gov.justice.laa.dstew.access.utils.harness;

import org.springframework.test.web.reactive.server.WebTestClient;

public interface TestContextProvider extends AutoCloseable {
    WebTestClient webTestClient();
    <T> T getBean(Class<T> type);
}
