package uk.gov.justice.laa.dstew.access.pact;

/**
 * The provider state strings this service implements, exactly as consumers must write them in
 * {@code .given("...")}. Documented for consumers in {@code docs/pact-provider-states.md}.
 *
 * <p>These are the contract between teams. Renaming one breaks every consumer that uses it, so
 * agree changes with the consumer teams first.
 */
final class PactStates {

  // Applications
  static final String APPLICATIONS_EXIST = "applications exist";
  static final String CLIENT_INDIVIDUAL_EXISTS_FOR_APPLICATION_001 =
      "a client individual exists for application 00000000-0000-0000-0000-000000000001";
  static final String NO_MATCHING_SCA_APPLICATION_EXISTS =
      "that no matching special children act application already exists";
  static final String APPLICATION_SUBMITTED_EXISTS =
      "a submitted application 00000000-0000-0000-0000-000000000002 exists";
  static final String APPLICATION_ASSIGNED_EXISTS =
      "application 00000000-0000-0000-0000-000000000003 is ready for manual assessment"
          + " and assigned to the caseworker";
  static final String APPLICATION_UNASSIGNED_EXISTS =
      "application 00000000-0000-0000-0000-000000000004 is ready for manual assessment"
          + " and unassigned";
  static final String APPLICATION_GRANTED_EXISTS =
      "application 00000000-0000-0000-0000-000000000005 has been granted";
  static final String APPLICATION_WITH_NOTES_EXISTS =
      "application 00000000-0000-0000-0000-000000000006 has notes";
  static final String APPLICATIONS_LINKED =
      "applications 00000000-0000-0000-0000-000000000007 and"
          + " 00000000-0000-0000-0000-000000000008 are linked with 007 as lead";
  static final String NO_APPLICATION_WITH_ID =
      "no application exists with id 00000000-0000-0000-0000-00000000dead";
  static final String APPLICATION_READ_MODEL_LAGGING = "the application read model is lagging";

  // Work list
  static final String WORK_LIST_ITEMS_EXIST = "work list items exist";

  // Prior authorities
  static final String PRIOR_AUTHORITY_DRAFT_EXISTS =
      "a prior authority draft 00000000-0000-0000-0000-00000000a001 exists";
  static final String PRIOR_AUTHORITY_SUBMITTED_EXISTS =
      "a submitted prior authority 00000000-0000-0000-0000-00000000a002 exists";
  static final String PRIOR_AUTHORITY_ASSIGNED_EXISTS =
      "a submitted prior authority 00000000-0000-0000-0000-00000000a003 is assigned"
          + " to the caseworker";
  static final String PRIOR_AUTHORITY_DECIDED_EXISTS =
      "a decided prior authority 00000000-0000-0000-0000-00000000a004 exists";
  static final String PRIOR_AUTHORITY_WITH_DOCUMENT_EXISTS =
      "a prior authority draft 00000000-0000-0000-0000-00000000a005 has an uploaded document";
  static final String NO_PRIOR_AUTHORITY_WITH_ID =
      "no prior authority exists with id 00000000-0000-0000-0000-0000000adead";

  private PactStates() {}
}
