package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires command-side write authorisation through the declared target resolver. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWriteAccess {
  /** Returns the resolver bean class that supplies authoritative access targets. */
  Class<? extends WriteAccessTargetResolver<?, ?>> resolver();

  /** Returns the authorizer bean class that enforces access to the resolved targets. */
  Class<? extends WriteAccessAuthorizer<?>> authorizer();
}
