package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

@Component
public class IndividualFactoryImpl
    implements Factory<IndividualResponse, IndividualResponse.Builder> {
  @Override
  public IndividualResponse create() {
    return IndividualResponse.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.now())
        .details(Map.of("test", "content"))
        .type(IndividualType.CLIENT)
        .build();
  }

  @Override
  public IndividualResponse create(Consumer<IndividualResponse.Builder> customiser) {
    IndividualResponse individualResponse = create();
    IndividualResponse.Builder builder = individualResponse.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}
