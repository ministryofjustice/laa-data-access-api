package uk.gov.justice.laa.dstew.access.query.draft;

/**
 * Discriminator for the polymorphic {@code drafts} store, identifying what kind of entity a draft
 * row holds. Mirrors the submission types: a draft of a given type becomes a submission of the same
 * type once submitted.
 */
public enum DraftType {
  CIVIL_APPLICATION,
  PRIOR_AUTHORITY,
  CRIMINAL_APPLICATION,
  POST_SUBMISSION_EVIDENCE,
  RETURN_REASON,
  DECISION,
  NOTE
}
