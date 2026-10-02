package uk.gov.justice.laa.dstew.access.query.utils.security;

/** Immutable access restriction resolved while an authenticated request is active. */
public sealed interface ReadAccessScope
    permits UnrestrictedReadAccessScope, OfficeCodeReadAccessScope {}


