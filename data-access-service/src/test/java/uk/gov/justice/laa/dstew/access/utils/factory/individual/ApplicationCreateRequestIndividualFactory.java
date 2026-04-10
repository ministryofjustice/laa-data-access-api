package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

@Profile("unit-test")
@Component
public class ApplicationCreateRequestIndividualFactory
    extends BaseFactory<IndividualCreateRequest, IndividualCreateRequest.Builder> {

  public ApplicationCreateRequestIndividualFactory() {
    super(IndividualCreateRequest::toBuilder, IndividualCreateRequest.Builder::build);
  }

  @Override
  public IndividualCreateRequest createDefault() {
    return IndividualCreateRequest.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.now())
        .details(new HashMap<>(Map.of("test", "content")))
        .build();
  }

  @Override
  public IndividualCreateRequest createRandom() {
    return IndividualCreateRequest.builder()
        .firstName(faker.name().firstName())
        .lastName(faker.name().lastName())
        .dateOfBirth(getRandomDate())
        .details(new HashMap<>(Map.of("test", "content")))
        .build();
  }
}
