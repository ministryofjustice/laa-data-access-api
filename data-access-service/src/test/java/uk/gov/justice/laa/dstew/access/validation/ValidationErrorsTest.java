package uk.gov.justice.laa.dstew.access.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationErrorsTest {

//  @Test
//  void emptyShouldReturnValidationErrorsInstance() {
//    ValidationErrors ve = ValidationErrors.empty();
//    assertThat(ve).isNotNull();
//    assertThat(ve.errors()).isEmpty();
//  }
//
//  @Test
//  void addShouldAddError() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.add("error1");
//
//    List<String> errors = ve.errors();
//    assertThat(errors).containsExactly("error1");
//  }
//
//  @Test
//  void addIfShouldAddErrorWhenConditionTrue() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.addIf(true, "conditional-error");
//
//    assertThat(ve.errors()).containsExactly("conditional-error");
//  }
//
//  @Test
//  void addIfShouldNotAddErrorWhenConditionFalse() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.addIf(false, "conditional-error");
//
//    assertThat(ve.errors()).isEmpty();
//  }
//
//  @Test
//  void throwIfAnyShouldThrowValidationExceptionWhenErrorsExistWithCustomMessage() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.add("err1").add("err2");
//
//    ValidationException ex = assertThrows(
//        ValidationException.class,
//        () -> ve.throwIfAny("Custom message")
//    );
//
//    assertThat(ex.getMessage()).isEqualTo("Custom message");
//    assertThat(ex.errors()).containsExactly("err1", "err2");
//  }
//
//  @Test
//  void throwIfAnyShouldNotThrowWhenNoErrorsWithCustomMessage() {
//    ValidationErrors ve = ValidationErrors.empty();
//    assertThat(ve.throwIfAny("Custom message")).isSameAs(ve);
//  }
//
//  @Test
//  void throwIfAnyShouldThrowValidationExceptionWhenErrorsExistWithoutMessage() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.add("err1");
//
//    ValidationException ex = assertThrows(
//        ValidationException.class,
//        ve::throwIfAny
//    );
//
//    assertThat(ex.errors()).containsExactly("err1");
//  }
//
//  @Test
//  void throwIfAnyShouldNotThrowWhenNoErrorsWithoutMessage() {
//    ValidationErrors ve = ValidationErrors.empty();
//    assertThat(ve.throwIfAny()).isSameAs(ve);
//  }
//
//  @Test
//  void errorsShouldReturnCopyOfErrors() {
//    ValidationErrors ve = ValidationErrors.empty();
//    ve.add("err1");
//    List<String> errorsCopy = ve.errors();
//
//    assertThat(errorsCopy).containsExactly("err1");
//  }
}
