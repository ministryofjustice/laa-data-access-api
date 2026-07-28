package uk.gov.justice.laa.dstew.access.query.submission;

/**
 * Discriminator for the polymorphic {@code submissions} store, identifying what kind of entity a
 * submission row holds. Lets a single table carry every submission type, distinguished by this
 * column, rather than a table per type.
 */
public enum SubmissionType {
  CIVIL_APPLICATION,
  PRIOR_AUTHORITY,
  CRIMINAL_APPLICATION,
  POST_SUBMISSION_EVIDENCE,
  RETURN_REASON,
  DECISION,
  NOTE
}
