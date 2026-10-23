package uk.gov.justice.laa.dstew.access.security;

import java.lang.reflect.Method;

/** Provides a resolver with the command handler's explicitly marked resource argument. */
public record WriteAccessTargetResolutionContext(
    Method handlerMethod, Object resource, AggregateResource resourceMetadata) {}
