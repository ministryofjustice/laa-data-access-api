package uk.gov.justice.laa.dstew.access.config.jackson;

import org.springframework.boot.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonDeserialize;
import uk.gov.justice.laa.dstew.access.model.DecisionTimeRequested;

/** Rejects non-integer JSON values for DecisionTimeRequested hours and minutes. */
@JacksonMixin(DecisionTimeRequested.class)
public interface DecisionTimeRequestedMixin {

  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  void setHours(Integer hours);

  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  void setMinutes(Integer minutes);
}
