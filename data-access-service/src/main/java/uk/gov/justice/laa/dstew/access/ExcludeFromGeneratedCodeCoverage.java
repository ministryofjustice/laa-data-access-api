package uk.gov.justice.laa.dstew.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.processing.Generated;

/** Annotation to exclude methods, classes, or constructors from generated code coverage reports. */
@Generated("ExcludeFromGeneratedCodeCoverage")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface ExcludeFromGeneratedCodeCoverage {}
