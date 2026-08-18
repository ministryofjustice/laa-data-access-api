package uk.gov.justice.laa.dstew.access.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.PayloadValidator;

/** Wires the copied application-content stack without coupling it to Spring. */
@Configuration
public class CreateApplicationConfig {

  @Bean
  PayloadValidator payloadValidator(ObjectMapper objectMapper, Validator validator) {
    return new PayloadValidator(objectMapper, validator);
  }

  @Bean
  ApplicationContentParser applicationContentParser(PayloadValidator payloadValidator) {
    return new ApplicationContentParser(payloadValidator);
  }
}
