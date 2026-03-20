package uk.gov.justice.laa.dstew.access.utils.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method as a smoke test.
 * <p>
 * When the test suite is run in infrastructure mode ({@code -Dtest.mode=infrastructure}),
 * only tests annotated with {@code @SmokeTest} will execute — all others are skipped.
 * In normal integration mode this annotation has no effect and all tests run as usual.
 * <p>
 * Can be applied at the class level (all tests in the class become smoke tests)
 * or at individual method level for finer-grained control.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface SmokeTest {
}

