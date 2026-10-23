package uk.gov.justice.laa.dstew.access.security;

import java.util.List;

/** Resolves authoritative write-access targets for a command handler invocation. */
public interface WriteAccessTargetResolver<C, T extends WriteAccessTarget> {
  Class<C> commandType();

  Class<T> targetType();

  default boolean requiresHandlerResource() {
    return false;
  }

  List<T> resolve(C command, WriteAccessTargetResolutionContext context);
}
