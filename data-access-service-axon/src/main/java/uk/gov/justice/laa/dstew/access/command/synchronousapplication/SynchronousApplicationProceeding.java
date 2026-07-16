package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/** Proceeding state owned by a SynchronousApplication aggregate. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SynchronousApplicationProceeding(
    UUID proceedingId,
    String id,
    String description,
    boolean leadProceeding,
    Proceeding proceeding) {}
