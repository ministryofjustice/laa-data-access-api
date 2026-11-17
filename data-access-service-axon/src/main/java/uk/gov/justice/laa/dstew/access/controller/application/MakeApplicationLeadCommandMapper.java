package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MakeApplicationLeadCommand;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Maps the make-lead request to its command-side contract. */
@Component
@RequiredArgsConstructor
public class MakeApplicationLeadCommandMapper {

  private final Clock clock;

  /** Maps a request for the supplied Application identifier. */
  public MakeApplicationLeadCommand toCommand(
      UUID applicationId, LinkedGroupChangeRequest request) {
    return toCommand(applicationId, request, null);
  }

  /** Maps a request and its authenticated caller for the supplied Application identifier. */
  public MakeApplicationLeadCommand toCommand(
      UUID applicationId, LinkedGroupChangeRequest request, AuthenticatedCaller caller) {
    if (request.getLinkedGroupVersion() == null) {
      throw new ValidationException(List.of("linkedGroupVersion: must not be null"));
    }
    return new MakeApplicationLeadCommand(
        applicationId,
        LinkedGroupVersionTokens.decode(request.getLinkedGroupVersion()),
        Instant.now(clock),
        caller,
        request.getEventHistory() == null ? null : request.getEventHistory().getEventDescription());
  }
}
