package uk.gov.justice.laa.dstew.access.security;

/** The policy facts for a resource protected by office-code write access. */
public record ProtectedResource(String officeCode) {}
