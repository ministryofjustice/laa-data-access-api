package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.UUID;

/** A linked-group event that removes one or more members. */
public sealed interface LinkedGroupMemberRemoval
    permits MemberRemovedFromGroupEvent, LinkedApplicationGroupDissolvedEvent {
  /** Attaches the accepted action's external payload reference. */
  LinkedGroupMemberRemoval withJustification(UUID id);
}
