package uk.gov.justice.laa.dstew.access.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;


/**
 * Configures the Jackson ObjectMapper to use the latest features and optimizations.
 */
@Configuration
public class JacksonConfig {

  /**
   * Creates a customized ObjectMapper that is configured for Jackson 2.x features.
   *
   * @return a configured ObjectMapper instance
   */
  @Bean
  public ObjectMapper objectMapper() {
    return MapperUtil.getObjectMapper();
  }
}
