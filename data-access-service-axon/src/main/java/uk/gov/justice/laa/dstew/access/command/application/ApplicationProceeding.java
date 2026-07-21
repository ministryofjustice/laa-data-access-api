package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

/** Proceeding state owned by a Application aggregate. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ApplicationProceeding(
    UUID proceedingId,
    String id,
    String description,
    boolean leadProceeding,
    Proceeding proceeding) {}
