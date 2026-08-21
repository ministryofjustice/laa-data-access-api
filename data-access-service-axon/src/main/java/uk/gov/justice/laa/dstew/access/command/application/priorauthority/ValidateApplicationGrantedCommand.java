package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Validates that the targeted application has an overall decision of {@code GRANTED}, a
 * prerequisite for prior-authority processing.
 */
@Command(routingKey = "applicationId")
public record ValidateApplicationGrantedCommand(@TargetEntityId UUID applicationId) {}
