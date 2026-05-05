package uk.gov.justice.laa.dstew.access.usecase.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a use-case method that requires role enforcement. Evaluated by EnforceRoleAspect in the
 * infrastructure layer.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnforceRole {
  /** Any one of these roles is sufficient (OR semantics). */
  RequiredRole[] anyOf() default {};

  /** All of these roles must be present (AND semantics). */
  RequiredRole[] allOf() default {};
}
