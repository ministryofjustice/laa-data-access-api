package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Security annotation that restricts access to methods or types
 * to users with the "API.Admin" role.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@entra.hasAppRole('DSA Test OBO – Writer')")
public @interface AllowApiWriter {
}