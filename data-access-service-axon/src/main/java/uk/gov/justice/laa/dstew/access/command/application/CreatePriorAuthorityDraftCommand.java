package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to start a new prior authority draft against an application. Carries no personal data:
 * the draft body has already been written to the deletable {@code prior_authority_drafts} table by
 * the application layer under {@code priorAuthorityId}. Prior authority is a post-submission
 * concern, so the aggregate guards that the target application is already {@code SUBMITTED}.
 */
public record CreatePriorAuthorityDraftCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID priorAuthorityId) {}
