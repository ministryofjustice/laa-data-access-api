package uk.gov.justice.laa.dstew.access.query.application.priorauthority.model;

import java.math.BigDecimal;

/** Disbursement details returned by the get-prior-authority use case. */
public record DisbursementDetails(String disbursementPurpose, BigDecimal disbursementAmount) {}
