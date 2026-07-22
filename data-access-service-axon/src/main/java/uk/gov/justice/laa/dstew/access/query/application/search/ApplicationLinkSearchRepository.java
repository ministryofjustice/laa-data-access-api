package uk.gov.justice.laa.dstew.access.query.application.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationLinkSearchRepository
    extends JpaRepository<ApplicationLinkSearchView, ApplicationLinkSearchView.ApplicationLinkId> {

  @Query(
      """
      SELECT l FROM ApplicationLinkSearchView l
      WHERE l.id.applicationId IN :applicationIds
      """)
  List<ApplicationLinkSearchView> findAllByApplicationIdIn(
      @Param("applicationIds") List<UUID> applicationIds);

  @Query(
      """
      SELECT l FROM ApplicationLinkSearchView l
      WHERE l.id.leadApplicationId IN :leadApplicationIds
      """)
  List<ApplicationLinkSearchView> findAllByLeadApplicationIdIn(
      @Param("leadApplicationIds") List<UUID> leadApplicationIds);
}
