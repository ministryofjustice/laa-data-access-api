package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

/** Classifies how an explicit application-link request should update membership routing. */
public enum ApplicationLinkAction {
  CREATE_GROUP,
  ADD_TO_EXISTING_GROUP,
  ALREADY_LINKED
}
