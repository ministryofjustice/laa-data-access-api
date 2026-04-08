package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method as a smoke test.
 * <p>
 * When the suite is run via the {@code infrastructureTest} Gradle task, only tests annotated
 * with {@code @SmokeTest} will execute — all others are excluded by the task's
 * {@code includeTags("smoke")} JUnit Platform filter. In normal integration mode
 * (the {@code integrationTest} task) this annotation has no effect and all tests run as usual.
 * <p>
 * Can be applied at the class level (all tests in the class become smoke tests)
 * or at individual method level for finer-grained control.
 * <p>
 * Also acts as a JUnit {@code @Tag("smoke")} so that the {@code infrastructureTest} task's
 * {@code useJUnitPlatform { includeTags 'smoke' }} selects only these tests without producing
 * skipped entries for the non-selected tests.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("smoke")
public @interface SmokeTest {
}
