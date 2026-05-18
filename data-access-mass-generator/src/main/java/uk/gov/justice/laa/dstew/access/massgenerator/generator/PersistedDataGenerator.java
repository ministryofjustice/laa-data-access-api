package uk.gov.justice.laa.dstew.access.massgenerator.generator;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullCertificateGenerator;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@Component
public class PersistedDataGenerator extends DataGenerator {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private EntityManager entityManager;

  // Map generator class to repository class
  private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap =
      new HashMap<>();

  @PostConstruct
  public void init() {
    registerRepository(DomainEventGenerator.class, DomainEventRepository.class);
    registerRepository(ApplicationEntityGenerator.class, ApplicationRepository.class);
    registerRepository(CaseworkerGenerator.class, CaseworkerRepository.class);
    registerRepository(ProceedingsEntityGenerator.class, ProceedingRepository.class);
    registerRepository(CertificateEntityGenerator.class, CertificateRepository.class);
    registerRepository(IndividualEntityGenerator.class, IndividualRepository.class);
    // Full* generator keys used directly in the mass-generator decision block
    registerRepository(FullCertificateGenerator.class, CertificateRepository.class);
  }

  public <TGenerator, TRepository extends JpaRepository<?, ?>> void registerRepository(
      Class<TGenerator> generatorType, Class<TRepository> repositoryType) {
    generatorRepoMap.put(generatorType, repositoryType);
  }

  @SuppressWarnings("unchecked")
  private <TEntity, TGenerator> JpaRepository<TEntity, ?> getRepository(
      Class<TGenerator> generatorType) {
    Class<? extends JpaRepository<?, ?>> repoClass = generatorRepoMap.get(generatorType);
    if (repoClass == null) {
      throw new IllegalArgumentException(
          "No repository registered for generator: " + generatorType.getName());
    }
    return (JpaRepository<TEntity, ?>) applicationContext.getBean(repoClass);
  }

  public <TEntity, TGenerator> TEntity persist(Class<TGenerator> generatorType, TEntity entity) {
    JpaRepository<TEntity, ?> repository = getRepository(generatorType);
    repository.saveAndFlush(entity);
    return entity;
  }

  public <TEntity, TGenerator> List<TEntity> persist(
      Class<TGenerator> generatorType, List<TEntity> entities) {
    JpaRepository<TEntity, ?> repository = getRepository(generatorType);
    repository.saveAllAndFlush(entities);
    return entities;
  }

  @Transactional
  public void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  @Transactional
  public void flush() {
    entityManager.flush();
  }

  @Transactional
  public void clear() {
    entityManager.clear();
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      List<TEntity> createAndPersistMultipleRandom(Class<TGenerator> generatorType, int count) {
    List<TEntity> entities = DataGenerator.createMultipleRandom(generatorType, count);
    if (entities.isEmpty()) {
      return entities;
    }
    return persist(generatorType, entities);
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      TEntity createAndPersist(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
    TEntity entity = DataGenerator.createDefault(generatorType, customiser);
    return persist(generatorType, entity);
  }

  /**
   * Links two existing applications together by establishing a relationship where one application
   * is associated with another lead application.
   *
   * <p>This method retrieves both applications by their IDs, adds the associated application to the
   * lead application's linked applications collection, and persists the relationship to the
   * database.
   *
   * @param leadId the UUID of the lead application to which the associated application will be
   *     linked
   * @param associatedId the UUID of the application to be linked as an associated application
   * @throws IllegalArgumentException if either application ID is not found in the database
   */
  @Transactional
  public void linkApplications(UUID leadId, UUID associatedId) {
    ApplicationEntity lead = entityManager.find(ApplicationEntity.class, leadId);
    ApplicationEntity associated = entityManager.find(ApplicationEntity.class, associatedId);
    LinkedApplicationRepository linkedApplicationRepository =
        applicationContext.getBean(LinkedApplicationRepository.class);
    linkedApplicationRepository.save(
        LinkedApplicationEntity.builder()
            .leadApplicationId(lead.getId())
            .associatedApplicationId(associated.getId())
            .build());
  }
}
