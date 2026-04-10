package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class IndividualFactory extends BaseFactory<IndividualResponse, IndividualResponse.Builder> {

  public IndividualFactory() {
    super(IndividualResponse::toBuilder, IndividualResponse.Builder::build);
  }

  @Override
  public IndividualResponse createDefault() {
    return IndividualResponse.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.now())
        .details(new HashMap<>(Map.of("test", "content")))
        .build();
  }

  @Override
  public IndividualResponse createRandom() {
    return IndividualResponse.builder()
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .dateOfBirth(getRandomDate())
        .details(new HashMap<>(Map.of("test", "content")))
        .build();
  }
}
