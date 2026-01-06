package uk.gov.justice.laa.dstew.access;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface ExcludeFromGeneratedCodeCoverage {
}
