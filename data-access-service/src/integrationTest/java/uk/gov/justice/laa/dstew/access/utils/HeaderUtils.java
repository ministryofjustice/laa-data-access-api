package uk.gov.justice.laa.dstew.access.utils;

public class HeaderUtils {
    public static String GetUUIDFromLocation(String location) {
        return location.replace("http://localhost" + TestConstants.URIs.CREATE_APPLICATION + "/", "");
    }
}
