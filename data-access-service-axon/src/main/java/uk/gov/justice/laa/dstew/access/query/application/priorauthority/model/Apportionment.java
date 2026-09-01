package uk.gov.justice.laa.dstew.access.query.application.priorauthority.model;

import java.math.BigDecimal;

/** Division of an expert cost across parties. */
public record Apportionment(Integer partiesSharingCosts, BigDecimal clientShareAmount) {}
