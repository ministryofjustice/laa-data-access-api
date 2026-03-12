package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.NonNull;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Utility class for configuring and providing a Jackson ObjectMapper instance.
 */
@ExcludeFromGeneratedCodeCoverage
public class MapperUtil {

  /**
   * Creates and configures a Jackson ObjectMapper.
   *
   * @return a configured ObjectMapper instance
   */
  public static @NonNull ObjectMapper getObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    JavaTimeModule timeModule = new JavaTimeModule();
    timeModule.addSerializer(Instant.class, new JsonSerializer<>() {
          @Override
          public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.truncatedTo(ChronoUnit.MICROS).toString());
          }
        }
    );
    mapper.registerModule(timeModule);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }
}
