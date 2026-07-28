package uk.gov.justice.laa.dstew.access.command.application;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to create or overwrite an application draft. Carries no personal data: the draft body is
 * persisted to the deletable {@code drafts} table by the application layer and this command is a
 * PII-free pointer identified only by the application id.
 *
 * <p>The application id is client-supplied for now (an interim accommodation while Civil Apply are
 * not yet ready to consume a service-minted id); in the target flow the datastore mints it at draft
 * creation. Idempotent create-or-update: a fresh id begins the draft (genesis), an existing draft
 * is updated in place.
 */
public record PutDraftApplicationCommand(
    @TargetAggregateIdentifier java.util.UUID applyApplicationId) {}
