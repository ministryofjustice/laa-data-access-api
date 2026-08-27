package uk.gov.justice.laa.dstew.access.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration class for cache beans. */
@Configuration
public class CacheConfig {

  @Bean
  CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("tokenCache");
  }
}
