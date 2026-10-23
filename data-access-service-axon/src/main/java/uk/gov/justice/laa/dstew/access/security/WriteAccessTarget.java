package uk.gov.justice.laa.dstew.access.security;

import java.util.UUID;

/** An authoritative command-side resource used to make a write-access decision. */
public interface WriteAccessTarget {
  String resourceType();

  UUID resourceId();
}
