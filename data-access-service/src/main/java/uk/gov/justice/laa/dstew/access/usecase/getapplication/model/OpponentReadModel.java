package uk.gov.justice.laa.dstew.access.usecase.getapplication.model;

import lombok.Builder;

/** Read-model record for a single opponent extracted from application merits data. */
@Builder(toBuilder = true)
public record OpponentReadModel(
    String opponentType, String firstName, String lastName, String organisationName) {}
