package uk.gov.justice.laa.dstew.access.utils;

import java.net.URI;
import java.util.UUID;

public class HeaderUtils {
    public static UUID GetUUIDFromLocation(String location) {
        String path = URI.create(location).getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        return UUID.fromString(id);
    }
}
