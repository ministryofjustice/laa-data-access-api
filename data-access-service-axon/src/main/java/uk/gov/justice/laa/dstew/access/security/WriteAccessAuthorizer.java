package uk.gov.justice.laa.dstew.access.security;

import java.util.List;

/** Applies a policy to one type of authoritative command-side write target. */
public interface WriteAccessAuthorizer<T extends WriteAccessTarget> {
  Class<T> targetType();

  void requireWriteAccess(List<T> targets, AuthenticatedCaller caller);
}
