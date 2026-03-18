package uk.gov.justice.laa.dstew.access.utils;

import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.SimpleThreadScope;

/**
 * Test configuration that registers a SimpleThreadScope to replace RequestScope
 * in unit tests where there is no actual HTTP request context.
 */
@Configuration
public class TestScopeConfig {

    @Bean
    public static CustomScopeConfigurer customScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("request", new SimpleThreadScope());
        return configurer;
    }
}
