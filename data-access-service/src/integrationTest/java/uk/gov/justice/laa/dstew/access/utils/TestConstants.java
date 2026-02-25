package uk.gov.justice.laa.dstew.access.utils;

public class TestConstants {

    public static class MediaTypes {
        public static final String APPLICATION_JSON = "application/json";
    }

    public static class URIs {
        public static final String GET_APPLICATION = "/api/v0/applications/{id}";
        public static final String CREATE_APPLICATION = "/api/v0/applications";
        public static final String GET_APPLICATIONS = "/api/v0/applications";
        public static final String UPDATE_APPLICATION = "/api/v0/applications/{id}";
        public static final String ASSIGN_CASEWORKER = "/api/v0/applications/assign";
        public static final String UNASSIGN_CASEWORKER = "/api/v0/applications/{id}/unassign";
        public static final String APPLICATION_HISTORY_SEARCH = "/api/v0/applications/{id}/history-search";
        public static final String ASSIGN_DECISION = "/api/v0/applications/{id}/decision";

        public static final String GET_CASEWORKERS = "/api/v0/caseworkers";
        public static final String GET_INDIVIDUALS = "/api/v0/individuals";
    }

    public static class Roles {
        public static final String READER = "APPROLE_DSA Test OBO – Reader";
        public static final String WRITER = "APPROLE_DSA Test OBO – Writer";
        public static final String UNKNOWN = "Unknown-DO-NOT-IMPLEMENT";
    }
}
