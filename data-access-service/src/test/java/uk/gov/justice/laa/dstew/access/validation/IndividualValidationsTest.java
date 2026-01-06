package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.model.Individual;

public class IndividualValidationsTest {

    IndividualValidations individualValidator = new IndividualValidations();

//    @Test
//    void shouldReturnValidationErrorsWhenIndividualIsNull() {
//        Individual individual = null;
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().stream().findFirst().get()).isEqualTo("Individual cannot be null.");
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @ValueSource(strings = {"", "    "})
//    void shouldReturnValidationErrorsWhenFirstNameIsNullEmptyOrWhitespace(String firstName) {
//        Individual individual = getFullyValidBuilder().firstName(firstName).build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().getFirst()).isEqualTo("First name must be populated.");
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @ValueSource(strings = {"", "    "})
//    void shouldReturnValidationErrorsWhenLastNameIsNullEmptyOrWhitespace(String lastName) {
//        Individual individual = getFullyValidBuilder().lastName(lastName).build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().getFirst()).isEqualTo("Last name must be populated.");
//    }
//
//    @Test
//    void shouldReturnValidationErrorsWhenDetailsAreNull() {
//        Individual individual = getFullyValidBuilder().details(null).build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().getFirst()).isEqualTo("Individual details must be populated.");
//    }
//
//    @Test
//    void shouldReturnValidationErrorsWhenDetailsAreEmpty() {
//        HashMap<String, Object> map = new HashMap<>();
//        Individual individual = getFullyValidBuilder().details(map).build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().getFirst()).isEqualTo("Individual details must be populated.");
//    }
//
//    @Test
//    void shouldReturnValidationErrorWhenDateOfBirthIsNull() {
//        Individual individual = getFullyValidBuilder().dateOfBirth(null).build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(1);
//        assertThat(result.errors().getFirst()).isEqualTo("Date of birth must be populated.");
//    }
//
//    @Test
//    void shouldReturnMultipleValidationErrorsWhenMultiplePropertiesInvalid() {
//        Individual individual = getFullyValidBuilder().firstName(null)
//                                                      .lastName(null)
//                                                      .details(Map.of("foo", "bar"))
//                                                      .build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(2);
//        assertThat(result.errors().getFirst()).isEqualTo("First name must be populated.");
//        assertThat(result.errors().getLast()).isEqualTo("Last name must be populated.");
//    }
//
//    @Test
//    void shouldReturnNoValidationErrorsWhenFieldsAreSet() {
//        Individual individual = getFullyValidBuilder().build();
//
//        var result = individualValidator.validateIndividual(individual);
//
//        assertThat(result.errors()).hasSize(0);
//    }
//
//    private static Individual.Builder getFullyValidBuilder() {
//        return Individual.builder()
//                         .firstName("John")
//                         .lastName("Doe")
//                         .dateOfBirth(LocalDate.of(2025, 11, 25))
//                         .details(Map.of("contactNumber","07123456789"));
//    }
}
