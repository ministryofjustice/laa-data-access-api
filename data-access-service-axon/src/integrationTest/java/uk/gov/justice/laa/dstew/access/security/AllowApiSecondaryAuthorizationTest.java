package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** Restricts the secondary-authorization integration-test endpoint to its test app role. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@entra.hasAppRole('SECONDARY_AUTHORIZATION_TEST') && @entra.hasName()")
public @interface AllowApiSecondaryAuthorizationTest {}
