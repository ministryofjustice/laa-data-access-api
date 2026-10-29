package uk.gov.justice.laa.dstew.access.security;

/** An authoritative resource whose write access is governed by its office code. */
public interface OfficeCodeOwned {
  String getOfficeCode();
}
