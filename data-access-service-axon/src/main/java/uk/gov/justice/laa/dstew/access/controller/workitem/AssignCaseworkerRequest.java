package uk.gov.justice.laa.dstew.access.controller.workitem;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for assigning a caseworker to a work item. */
public record AssignCaseworkerRequest(@NotNull UUID caseworkerId) {}
