package uk.gov.justice.laa.dstew.access.pact;

import java.util.UUID;

/**
 * Fixed identifiers used by the provider states, shared with consumers through {@code
 * docs/pact-provider-states.md}.
 *
 * <p>Every state owns its own identifiers because the in-memory database is shared by every
 * interaction in a verification run and interactions replay in no fixed order. A state that mutates
 * an application (assigns it, decides it) must never share an ID with a state that expects that
 * application untouched.
 */
final class PactIds {

  /** Entra OID the dev token resolves to; see {@code SecurityConfig.DEV_TOKEN_ENTRA_OID}. */
  static final UUID DEV_CASEWORKER = UUID.fromString("00000000-0000-0000-0000-000000000001");

  // Applications. The consumer supplies application IDs, so fixed values are the natural choice.
  static final UUID APPLICATION_001 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID APPLICATION_SUBMITTED = UUID.fromString("00000000-0000-0000-0000-000000000002");
  static final UUID APPLICATION_ASSIGNED = UUID.fromString("00000000-0000-0000-0000-000000000003");
  static final UUID APPLICATION_UNASSIGNED =
      UUID.fromString("00000000-0000-0000-0000-000000000004");
  static final UUID APPLICATION_GRANTED = UUID.fromString("00000000-0000-0000-0000-000000000005");
  static final UUID APPLICATION_WITH_NOTES =
      UUID.fromString("00000000-0000-0000-0000-000000000006");
  static final UUID APPLICATION_LEAD = UUID.fromString("00000000-0000-0000-0000-000000000007");
  static final UUID APPLICATION_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000008");
  static final UUID APPLICATION_PA_PARENT = UUID.fromString("00000000-0000-0000-0000-000000000009");
  static final UUID APPLICATION_MISSING = UUID.fromString("00000000-0000-0000-0000-00000000dead");

  // Prior authorities. The API generates these, but a state handler may fix them when seeding, so
  // consumers get stable IDs for reads. Only uploaded document IDs remain server-generated.
  static final UUID PRIOR_AUTHORITY_DRAFT = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  static final UUID PRIOR_AUTHORITY_SUBMITTED =
      UUID.fromString("00000000-0000-0000-0000-00000000a002");
  static final UUID PRIOR_AUTHORITY_ASSIGNED =
      UUID.fromString("00000000-0000-0000-0000-00000000a003");
  static final UUID PRIOR_AUTHORITY_DECIDED =
      UUID.fromString("00000000-0000-0000-0000-00000000a004");
  static final UUID PRIOR_AUTHORITY_WITH_DOCUMENT =
      UUID.fromString("00000000-0000-0000-0000-00000000a005");
  static final UUID PRIOR_AUTHORITY_MISSING =
      UUID.fromString("00000000-0000-0000-0000-0000000adead");

  private PactIds() {}
}
