package uk.gov.justice.laa.dstew.access.query.workitem;

/** Discriminates the kind of thing a work item represents in a caseworker queue. */
public enum WorkType {

  /** A submitted application that needs caseworker attention. */
  APPLICATION,

  /** A submitted prior authority that needs caseworker attention. */
  PRIOR_AUTHORITY
}
