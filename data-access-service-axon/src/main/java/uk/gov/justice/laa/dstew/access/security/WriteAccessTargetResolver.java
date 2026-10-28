package uk.gov.justice.laa.dstew.access.security;

import java.util.List;

/** Resolves authoritative protected resources for a command handler invocation. */
public interface WriteAccessTargetResolver {
  List<ProtectedResource> resolve(Object resource);
}
