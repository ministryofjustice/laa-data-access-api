package uk.gov.justice.laa.dstew.access.query.application.priorauthority.model;

import java.math.BigDecimal;

/** Expert cost details returned by the get-prior-authority use case. */
public record ExpertCosts(
    BillingType billingType,
    BigDecimal hourlyRate,
    TimeRequested timeRequested,
    BigDecimal totalAmount,
    Boolean costsSharedWithOtherParties,
    Apportionment apportionment) {}
