package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/** Proceeding state owned by an Application aggregate. */
public record ApplicationProceeding(
    UUID proceedingId,
    UUID applyProceedingId,
    String description,
    boolean lead,
    Proceeding proceedingContent) {}
