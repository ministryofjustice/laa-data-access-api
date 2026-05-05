package uk.gov.justice.laa.dstew.access.massgenerator.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationJobJpaRepository extends JpaRepository<GenerationJobEntity, String> {
  List<GenerationJobEntity> findAllByOrderByCreatedAtDesc();
}
