package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identifies an aggregate argument used to resolve a generic write-access target. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateResource {
  /** Returns the domain type identifying the aggregate resource. */
  String resourceType();

  /** Returns the name of the aggregate UUID identifier property. */
  String resourceIdProperty();
}
