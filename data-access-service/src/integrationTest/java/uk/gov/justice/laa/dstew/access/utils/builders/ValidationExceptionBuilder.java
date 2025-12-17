package uk.gov.justice.laa.dstew.access.utils.builders;

import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import java.util.List;

public class ValidationExceptionBuilder {
    private List<String> errors;

    public static ValidationExceptionBuilder create() {
        return new ValidationExceptionBuilder();
    }

    public ValidationExceptionBuilder errors(List<String> errors) {
        this.errors = errors;
        return this;
    }

    public ValidationException build() {
        return new ValidationException(this.errors);
    }
}
