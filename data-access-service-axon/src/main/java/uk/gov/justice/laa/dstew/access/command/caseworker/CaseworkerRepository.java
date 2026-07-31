package uk.gov.justice.laa.dstew.access.command.caseworker;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence interface for the Axon-owned caseworker directory. */
public interface CaseworkerRepository extends JpaRepository<Caseworker, UUID> {}
