package uk.gov.justice.laa.dstew.access.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import uk.gov.justice.laa.dstew.access.model.Individual;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

public class IndividualValidationsTest {

    IndividualValidations individualValidator = new IndividualValidations();

    @Test 
    void shouldReturnValidationErrorsWhenIndividualIsNull() {
        Individual individual = null;
        
        var result = individualValidator.validateIndividual(individual);

        assertThat(result).hasSize(1);
        assertThat(result.stream().findFirst().get()).isEqualTo("Individual cannot be null.");
    }

    @ParameterizedTest  
    @NullSource
    @ValueSource(strings = {"", "    "})  
    void shouldReturnValidationErrorsWhenFirstNameIsNullEmptyOrWhitespace(String firstName) {
        Individual individual = Individual.builder().firstName(firstName).build();

        var result = individualValidator.validateIndividual(individual);

        assertThat(result).contains("First name must be populated.");
    }

    @ParameterizedTest  
    @NullSource
    @ValueSource(strings = {"", "    "})  
    void shouldReturnValidationErrorsWhenLastNameIsNullEmptyOrWhitespace(String lastName) {
        Individual individual = Individual.builder().lastName(lastName).build();

        var result = individualValidator.validateIndividual(individual);

        assertThat(result).contains("Last name must be populated.");
    }

    @Test
    void shouldReturnValidationErrorsWhenDetailsAreNull() {
        Individual individual = Individual.builder().details(null).build();

        var result = individualValidator.validateIndividual(individual);

        assertThat(result).contains("Individual details must be populated.");
    }

    @Test
    void shouldReturnValidationErrorsWhenDetailsAreEmpty() {
        HashMap<String, Object> map = new HashMap<>();
        Individual individual = Individual.builder().details(map).build();

        var result = individualValidator.validateIndividual(individual);

        assertThat(result).contains("Individual details must be populated.");
    }

    void shouldReturnMultipleValidationErrorsWhenMultiplePropertiesInvalid() {
        Individual individual = Individual.builder().details(Map.of("foo", "bar")).build();

        var result = individualValidator.validateIndividual(individual);

        assertThat(result).contains("First name must be populated.");
        assertThat(result).contains("Last name must be populated.");
    }

    @Test
    void shouldReturnNoValidationErrorsWhenFieldsAreSet() {
        Individual individual = Individual.builder()
                                          .firstName("John")
                                          .lastName("Doe")
                                          .details(Map.of("contactNumber","07123456789"))
                                          .build();
        var result = individualValidator.validateIndividual(individual);
        assertThat(result).hasSize(0);
    }
}
