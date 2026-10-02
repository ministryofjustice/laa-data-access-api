package uk.gov.justice.laa.dstew.access.query.utils.security;

/** Indicates that no row-level restriction applies to a read. */
public record UnrestrictedReadAccessScope() implements ReadAccessScope {}


