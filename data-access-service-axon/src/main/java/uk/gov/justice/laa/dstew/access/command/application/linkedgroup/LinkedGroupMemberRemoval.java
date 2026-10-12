package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

/** A linked-group event that removes one or more members. */
public sealed interface LinkedGroupMemberRemoval
    permits MemberRemovedFromGroupEvent, LinkedApplicationGroupDissolvedEvent {}
