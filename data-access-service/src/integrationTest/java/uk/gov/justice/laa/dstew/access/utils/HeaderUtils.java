package uk.gov.justice.laa.dstew.access.utils;

import java.util.UUID;

public class HeaderUtils {
  public static UUID GetUUIDFromLocation(String location) {
    var id = location.replace("http://localhost" + TestConstants.URIs.CREATE_APPLICATION + "/", "");
    return UUID.fromString(id);
  }
}
