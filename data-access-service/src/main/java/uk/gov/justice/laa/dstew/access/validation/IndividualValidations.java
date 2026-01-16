package uk.gov.justice.laa.dstew.access.validation;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.Individual;

/** Class that runs validations for {@link Individual} API model. */
@Component
public class IndividualValidations {

  /**
   * Validates the {@link Individual} API model to ensure correct fields are present.
   *
   * @param individual API model the {@link Individual} to validate
   * @return a list of the validation issues, such as missing fields, if the individual is null will
   *     only return that validation
   */
  ValidationErrors validateIndividual(Individual individual) {
    if (individual == null) {
      return ValidationErrors.empty().add("Individual cannot be null.");
    }

    return ValidationErrors.empty()
        .addIf(isFirstNamePopulated(individual), "First name must be populated.")
        .addIf(isLastNamePopulated(individual), "Last name must be populated.")
        .addIf(isDetailsPopulated(individual), "Individual details must be populated.")
        .addIf(isDateOfBirthPopulated(individual), "Date of birth must be populated.");
  }

  private static boolean isFirstNamePopulated(Individual individual) {
    return individual.getFirstName() == null || individual.getFirstName().isBlank();
  }

  private static boolean isLastNamePopulated(Individual individual) {
    return individual.getLastName() == null || individual.getLastName().isBlank();
  }

  private static boolean isDetailsPopulated(Individual individual) {
    return individual.getDetails() == null || individual.getDetails().isEmpty();
  }

  private static boolean isDateOfBirthPopulated(Individual individual) {
    return individual.getDateOfBirth() == null;
  }
}
