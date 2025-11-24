package uk.gov.justice.laa.dstew.access.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.Individual;

/**
 * Class that runs validations for {@link Individual} API model.
 */
@Component
public class IndividualValidations {

  /**
  * Validates the {@link Individual} API model to ensure correct fields are present.
  *
  * @param individual API model the {@link Individual} to validate
  * @return a list of the validation issues, such as missing fields, if the individual is null will only return that validation
  */
  List<String> validateIndividual(Individual individual) {
    if (individual == null) {
      return List.of("Individual cannot be null.");
    }
    List<String> validationErrors = new ArrayList<>();

    if (individual.getFirstName() == null || individual.getFirstName().isBlank()) {
      validationErrors.add("First name must be populated.");
    }

    if (individual.getLastName() == null || individual.getLastName().isBlank()) {
      validationErrors.add("Last name must be populated.");
    }

    if (individual.getDetails() == null || individual.getDetails().isEmpty()) {
      validationErrors.add("Individual details must be populated.");
    }
    return validationErrors;
  }
}
