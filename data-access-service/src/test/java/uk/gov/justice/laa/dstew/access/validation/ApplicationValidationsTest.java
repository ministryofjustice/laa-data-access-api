package uk.gov.justice.laa.dstew.access.validation;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

@ExtendWith(MockitoExtension.class)
public class ApplicationValidationsTest {

  @Mock
  EffectiveAuthorizationProvider mockEntra;
  @Mock
  IndividualValidations individualValidator;

  @InjectMocks
  ApplicationValidations classUnderTest;

  @Test
  void shouldThrowCreateRequestValidationErrorWhenRequestIsNull() {
    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(null));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenContentIsNull() {
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                               .applicationContent(null)
                                                               .build();

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenContentIsEmpty() {
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                               .applicationContent(new HashMap<>())
                                                               .build();

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldThrowCreateRequestValidationErrorWhenReferenceIsNull(){
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                                .applicationContent(Map.of("foo", "bar"))
                                                                .applicationReference(null)
                                                                .build();

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationCreateRequest(request),
        "Application reference cannot be blank");
  }


  @ParameterizedTest  
  @ValueSource(strings = {"", "    "})  
  void shouldCreateRequestValidationThrowValidationErrorWhenReferenceIsNotValid(String applicationReference) {  
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                                .applicationContent(Map.of("foo", "bar"))
                                                                .applicationReference(applicationReference)
                                                                .build();
    assertThrows(ValidationException.class,  
        () -> classUnderTest.checkApplicationCreateRequest(request),  
            "Application reference cannot be blank");  
  } 

  @Test
  void shouldNotThrowCreateRequestValidationErrorWhenContentIsValid() {
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                               .status(ApplicationStatus.SUBMITTED)
                                                               .applicationContent(Map.of("foo", "bar"))
                                                               .applicationReference("app-ref")
                                                               .build();
    assertDoesNotThrow(() -> classUnderTest.checkApplicationCreateRequest(request));
  }

  @Test
  void shouldDelegateIndividualValidationsToIndividualValidator() {
    var individuals = createIndividuals();
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                               .status(ApplicationStatus.SUBMITTED)
                                                               .applicationContent(Map.of("foo", "bar"))
                                                               .applicationReference("app-ref")
                                                               .individuals(individuals)
                                                               .build();
    when(individualValidator.validateIndividual(any(Individual.class)))
      .thenReturn(ValidationErrors.empty());
      
    classUnderTest.checkApplicationCreateRequest(request);
    
    individuals.forEach(i -> verify(individualValidator, times(1)).validateIndividual(i));
  }

  @Test
  void shouldOnlyReturnUniqueValidationErrors() {
    ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                                                               .status(ApplicationStatus.SUBMITTED)
                                                               .applicationContent(Map.of("foo", "bar"))
                                                               .applicationReference("app-ref")
                                                               .individuals(createIndividuals())
                                                               .build();
    
    when(individualValidator.validateIndividual(any(Individual.class)))
      .thenReturn(ValidationErrors.empty().add("ValidationError").add( "ValidationError"));
    
    ValidationException exception = assertThrows(ValidationException.class,  
        () -> classUnderTest.checkApplicationCreateRequest(request));
    assertThat(exception.errors()).hasSize(1);
    assertThat(exception.errors().stream().findFirst().get()).isEqualTo("ValidationError");
  }

  private static List<Individual> createIndividuals() {
    return Instancio.ofList(Individual.class)
                               .size(5)
                               .set(Select.field(Individual::getDetails), Map.of("",""))
                               .create();
  }

  // --- ApplicationUpdateRequest tests ---

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenRequestIsNull() {
    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(null, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenContentIsNull() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(null);

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void shouldThrowUpdateRequestValidationErrorWhenContentIsEmpty() {
    ApplicationUpdateRequest request = new ApplicationUpdateRequest();
    request.setApplicationContent(new HashMap<>());

    assertThrows(ValidationException.class,
        () -> classUnderTest.checkApplicationUpdateRequest(request, null));
  }

  @Test
  void notNullBooleanShouldReturnFalseWhenNull() {
    assert !ValidationUtils.notNull((Boolean) null);
  }

  @Test
  void notNullBooleanShouldReturnTrueWhenTrue() {
    assert ValidationUtils.notNull(true);
  }

  @Test
  void notNullStringShouldReturnEmptyStringWhenNull() {
    assert ValidationUtils.notNull((String) null).equals("");
  }

  @Test
  void notNullStringShouldReturnSameStringWhenNotNull() {
    assert ValidationUtils.notNull("hello").equals("hello");
  }
}