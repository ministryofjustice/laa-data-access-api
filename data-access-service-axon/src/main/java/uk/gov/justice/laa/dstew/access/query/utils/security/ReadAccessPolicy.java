package uk.gov.justice.laa.dstew.access.query.utils.security;

import org.springframework.data.jpa.domain.Specification;

/** Declares how an external read-access scope maps onto one read model. */
public interface ReadAccessPolicy<T> {
  Specification<T> restrictionFor(ReadAccessScope scope);
}
