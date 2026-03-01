package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;

@TestConfiguration
public class TestAsyncConfig {

    /**
     * Overrides the production applicationTaskExecutor with a synchronous executor for unit tests,
     * ensuring CompletableFuture tasks run inline and Mockito interactions are recorded deterministically.
     */
    @Bean(name = "applicationTaskExecutor")
    public SyncTaskExecutor applicationTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
