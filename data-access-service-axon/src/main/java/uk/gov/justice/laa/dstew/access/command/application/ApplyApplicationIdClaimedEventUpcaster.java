package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.upcasting.event.SingleEventUpcaster;
import org.springframework.stereotype.Component;

/** Migrates the original nested-event claim payload to finalisation details. */
@Component
public class ApplyApplicationIdClaimedEventUpcaster extends SingleEventUpcaster {

  private static final String EVENT_TYPE = ApplyApplicationIdClaimedEvent.class.getName();
  private static final SimpleSerializedType REVISION_ONE_TYPE =
      new SimpleSerializedType(EVENT_TYPE, "1");

  @Override
  protected boolean canUpcast(IntermediateEventRepresentation representation) {
    return EVENT_TYPE.equals(representation.getType().getName())
        && representation.getType().getRevision() == null;
  }

  @Override
  protected IntermediateEventRepresentation doUpcast(
      IntermediateEventRepresentation representation) {
    return representation.upcastPayload(
        REVISION_ONE_TYPE, JsonNode.class, this::moveApplicationFieldsToFinalisationDetails);
  }

  private JsonNode moveApplicationFieldsToFinalisationDetails(JsonNode payload) {
    ObjectNode upcastedPayload = payload.deepCopy();
    ObjectNode finalisationDetails = (ObjectNode) upcastedPayload.remove("applicationCreatedEvent");
    finalisationDetails.remove("applicationId");
    upcastedPayload.set("applicationFinalisationDetails", finalisationDetails);
    return upcastedPayload;
  }
}
