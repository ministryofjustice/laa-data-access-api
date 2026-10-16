package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires provider-office authorization before an annotated command handler executes. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOfficeCodeWriteAccess {}
