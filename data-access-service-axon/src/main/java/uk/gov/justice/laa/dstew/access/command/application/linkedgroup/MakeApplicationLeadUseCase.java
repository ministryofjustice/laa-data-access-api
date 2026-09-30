package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Thin controller-facing delegate for making an application the lead of its linked group. */
@Service
@RequiredArgsConstructor
public class MakeApplicationLeadUseCase {
  private final MakeApplicationLeadCommandHandler commandHandler;

  public void execute(MakeApplicationLeadCommand command) {
    commandHandler.handle(command);
  }
}
