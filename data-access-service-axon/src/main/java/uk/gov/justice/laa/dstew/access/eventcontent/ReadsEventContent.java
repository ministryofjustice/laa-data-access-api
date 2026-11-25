package uk.gov.justice.laa.dstew.access.eventcontent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a projection whose streaming processor receives event content. Every other processor
 * receives retained fields only. The projection must also carry {@code @Namespace}, which names its
 * processor.
 *
 * <p>Adding this annotation needs review: the projection's class javadoc must record what content
 * it stores and how erasure reaches it. Erasure reaches stored content only through replay:
 * replaying the projection after erasure rebuilds its tables without the erased content.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReadsEventContent {}
