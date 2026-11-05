package org.axonframework.modelling.repository;

/**
 * Test-only stand-in for Axon Framework's {@code EntityNotFoundException}, used to verify {@link
 * uk.gov.justice.laa.dstew.access.shared.logging.aspects.LoggingAspects} matches this
 * framework-defined, client-facing exception type by fully-qualified name without requiring a
 * compile-time dependency on axon-framework from {@code data-access-shared}.
 */
public class EntityNotFoundException extends RuntimeException {

  public EntityNotFoundException(String message) {
    super(message);
  }
}
