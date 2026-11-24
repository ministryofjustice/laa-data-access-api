package uk.gov.justice.laa.dstew.access.eventcontent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event record component that is kept on the stored event and survives erasure. Every
 * other component is erasable content, stored apart in {@code event_content} on append. Retain only
 * identity and genesis keys, values a decider rule uses, access and work-allocation values, the
 * event's own timestamp and non-personal outcomes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.RECORD_COMPONENT,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.METHOD
})
public @interface RetainedField {}
