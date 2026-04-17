package uk.gov.justice.laa.dstew.access.usecase.shared.security;

/** Roles that can be required by use case methods. */
public enum RequiredRole {
  AUTHENTICATED,
  API_CASEWORKER,
  ADMIN,
  SUPERVISOR
}
