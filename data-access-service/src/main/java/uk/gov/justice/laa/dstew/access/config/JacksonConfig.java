package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Jackson ObjectMapper configuration for JSON serialization/deserialization.
 */
@ExcludeFromGeneratedCodeCoverage
@Configuration
public class JacksonConfig {

  /**
   * Provides an ObjectMapper bean for JSON processing.
   *
   * @return configured ObjectMapper instance
   */
  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }
}

