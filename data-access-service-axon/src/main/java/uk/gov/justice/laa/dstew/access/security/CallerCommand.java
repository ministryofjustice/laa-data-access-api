package uk.gov.justice.laa.dstew.access.security;

/** A command that carries the authenticated caller facts required for write authorisation. */
public interface CallerCommand {
  AuthenticatedCaller caller();
}
