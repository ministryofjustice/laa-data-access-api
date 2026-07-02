package uk.gov.justice.laa.dstew.access.usecase.updateapplication;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/** Command record carrying all fields required to update an application. */
@Builder(toBuilder = true)
public record UpdateApplicationCommand(
    UUID id, String status, Map<String, Object> applicationContent) {}
