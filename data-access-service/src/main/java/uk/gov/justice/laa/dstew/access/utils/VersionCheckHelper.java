package uk.gov.justice.laa.dstew.access.utils;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Helper class for checking entity version locking.
 */
public class VersionCheckHelper {

  /**
   * Checks if the provided version matches the entity version for optimistic locking.
   *
   * @param id            the ID of the entity
   * @param entityVersion the current version of the entity
   * @param reqVersion    the version provided in the request
   * @throws OptimisticLockingFailureException if the versions do not match
   */
  public static void checkEntityVersionLocking(UUID id, Long entityVersion, @NotNull Long reqVersion) {
    if (!reqVersion.equals(entityVersion)) {
      throw new OptimisticLockingFailureException(
          String.format("Application with id %s and version %s not found", id, reqVersion));
    }
  }
}
