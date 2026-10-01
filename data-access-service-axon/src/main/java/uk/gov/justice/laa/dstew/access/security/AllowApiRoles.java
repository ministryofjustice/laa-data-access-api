package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Restricts access to authenticated callers that hold any of the given app roles and a non-blank
 * authenticated name.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@entra.hasAnyAppRole('{value}'.split(',')) && @entra.hasName()")
public @interface AllowApiRoles {

  String[] value();
}
