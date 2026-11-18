package uk.gov.justice.laa.dstew.access.shared.logging;

/**
 * Marks an exception as representing an expected, client-caused failure (validation, conflict,
 * not-found, forbidden, and similar conditions that are mapped to an HTTP 4xx response).
 *
 * <p>Exceptions implementing this interface are logged at {@code WARN} rather than {@code ERROR} by
 * {@link uk.gov.justice.laa.dstew.access.shared.logging.aspects.LoggingAspects}, and so are not
 * reported to Sentry via the log route: they are caller-driven outcomes, not operator- actionable
 * failures. Reserve {@code ERROR} for unexpected failures (infra/external-dependency failures, data
 * integrity violations, bugs) that map to an HTTP 5xx response or have no caller to report the
 * failure to.
 */
public interface ClientFacingException {}
