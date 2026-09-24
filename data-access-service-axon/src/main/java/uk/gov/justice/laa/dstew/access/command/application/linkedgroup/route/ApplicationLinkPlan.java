package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Describes the membership action required to link a source application to a target route. */
public record ApplicationLinkPlan(ApplicationLinkAction action, @Nullable UUID groupId) {

  /** Enforces that only create-group actions omit a group identifier. */
  public ApplicationLinkPlan {
    Objects.requireNonNull(action, "action must not be null");
    switch (action) {
      case CREATE_GROUP -> {
        if (groupId != null) {
          throw new IllegalArgumentException("CREATE_GROUP must not carry a group ID");
        }
      }
      case ADD_TO_EXISTING_GROUP -> {
        if (groupId == null) {
          throw new IllegalArgumentException("ADD_TO_EXISTING_GROUP must carry a group ID");
        }
      }
      case ALREADY_LINKED -> {
        if (groupId == null) {
          throw new IllegalArgumentException("ALREADY_LINKED must carry a group ID");
        }
      }
      default -> throw new IllegalStateException("Unhandled application link action: " + action);
    }
  }
}
