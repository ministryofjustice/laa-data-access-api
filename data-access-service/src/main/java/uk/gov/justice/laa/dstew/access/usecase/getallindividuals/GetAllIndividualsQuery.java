package uk.gov.justice.laa.dstew.access.usecase.getallindividuals;

import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/** Query record carrying all filter and pagination parameters for retrieving individuals. */
@Builder(toBuilder = true)
public record GetAllIndividualsQuery(
    @Nullable Integer page,
    @Nullable Integer pageSize,
    @Nullable UUID applicationId,
    @Nullable String individualType,
    @Nullable String include) {}
