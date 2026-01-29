package uk.gov.justice.laa.dstew.access.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.factory.proceeding.ProceedingFactory;

class ProceedingMapperTest {

  private final ProceedingMapper proceedingMapper = new ProceedingMapperImpl();

  private final ProceedingFactory proceedingFactory = new ProceedingFactory();

  @Test
  void requestToProceedingEntity() {
    UUID applicationId = UUID.randomUUID();
    Proceeding proceeding = proceedingFactory.createDefault();
    ProceedingEntity proceedingEntity = proceedingMapper.toProceedingEntity(proceeding, applicationId);

    assertEquals(proceedingEntity.getApplyProceedingId(), proceeding.getId());
    assertEquals(proceedingEntity.getApplicationId(), applicationId);
    assertEquals(proceedingEntity.getProceedingContent(), MapperUtil.getObjectMapper().convertValue(proceeding, Map.class));
    assertEquals(proceedingEntity.isLead(), proceeding.getLeadProceeding());
    assertEquals(proceedingEntity.getDescription(), proceeding.getDescription());

  }

}