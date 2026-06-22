package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/** Command record carrying all fields required to create an application. */
@Builder(toBuilder = true)
public record CreateApplicationCommand(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<IndividualCommand> individuals,
    String serialisedRequest) {}
