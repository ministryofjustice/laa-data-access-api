package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.List;

public final class PriorAuthorityRequestFactory {
  public List<PriorAuthorityRequest> createAll() {
    return List.of(
        new PriorAuthorityRequest(
            "EXPERT",
            "{\"priorAuthorityType\":\"EXPERT\",\"justification\":\"An independent expert assessment is required for this family matter.\",\"expertDetails\":{\"expertType\":\"Clinical psychologist\",\"expertFullName\":\"Casey Expert\",\"expertPostcode\":\"AB1 2CD\",\"expertCosts\":{\"billingType\":\"HOURLY\",\"hourlyRate\":300.0,\"timeRequested\":{\"hours\":2,\"minutes\":30},\"totalAmount\":900.0,\"costsSharedWithOtherParties\":true,\"apportionment\":{\"partiesSharingCosts\":2,\"clientShareAmount\":450.0}}}}"),
        new PriorAuthorityRequest(
            "DISBURSEMENT",
            "{\"priorAuthorityType\":\"DISBURSEMENT\",\"justification\":\"An interpreter is required to enable the client to participate effectively.\",\"disbursementDetails\":{\"disbursementPurpose\":\"Interpreter\",\"disbursementAmount\":150.25}}"),
        new PriorAuthorityRequest(
            "COUNSEL",
            "{\"priorAuthorityType\":\"COUNSEL\",\"justification\":\"Specialist counsel is required because of the complexity of the proceedings.\",\"counselDetails\":{\"counselType\":\"KINGS_COUNSEL_ALONE\"}}"));
  }

  public record PriorAuthorityRequest(String type, String request) {}
}
