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
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullCertificateGenerator;
import uk.gov.justice.laa.dstew.access.massgenerator.generator.application.FullMeritsDecisionGenerator;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@Component("massGeneratorPersistedDataGenerator")
public class PersistedDataGenerator {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private EntityManager entityManager;

  // Map generator class to repository class
  private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap =
      new HashMap<>();

  @PostConstruct
  public void init() {
    registerRepository(DomainEventGenerator.class, DomainEventRepository.class);
    registerRepository(ApplicationEntityGenerator.class, ApplicationRepository.class);
    registerRepository(ApplicationSummaryGenerator.class, ApplicationSummaryRepository.class);
    registerRepository(CaseworkerGenerator.class, CaseworkerRepository.class);
    registerRepository(DecisionEntityGenerator.class, DecisionRepository.class);
    registerRepository(ProceedingsEntityGenerator.class, ProceedingRepository.class);
    registerRepository(MeritsDecisionsEntityGenerator.class, MeritsDecisionRepository.class);
    registerRepository(CertificateEntityGenerator.class, CertificateRepository.class);
    registerRepository(IndividualEntityGenerator.class, IndividualRepository.class);
    // Full* generator keys used directly in the mass-generator decision block
    registerRepository(FullMeritsDecisionGenerator.class, MeritsDecisionRepository.class);
    registerRepository(FullCertificateGenerator.class, CertificateRepository.class);
  }

  public <TGenerator, TRepository extends JpaRepository<?, ?>> void registerRepository(
      Class<TGenerator> generatorType, Class<TRepository> repositoryType) {
    generatorRepoMap.put(generatorType, repositoryType);
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      TEntity createAndPersist(Class<TGenerator> generatorType) {
    TEntity entity = DataGenerator.createDefault(generatorType);
    return persist(generatorType, entity);
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      TEntity createAndPersist(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
    TEntity entity = DataGenerator.createDefault(generatorType, customiser);
    return persist(generatorType, entity);
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      List<TEntity> createAndPersistMultiple(Class<TGenerator> generatorType, int count) {
    List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count);
    if (entities.isEmpty()) {
      return entities;
    }
    return persist(generatorType, entities);
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      List<TEntity> createAndPersistMultiple(
          Class<TGenerator> generatorType, int count, Consumer<TBuilder> customiser) {
    List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count, customiser);
    if (entities.isEmpty()) {
      return entities;
    }
    return persist(generatorType, entities);
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
    repository.save(entity);
    return entity;
  }

  public <TEntity, TGenerator> List<TEntity> persist(
      Class<TGenerator> generatorType, List<TEntity> entities) {
    JpaRepository<TEntity, ?> repository = getRepository(generatorType);
    repository.saveAll(entities);
    return entities;
  }

  @Transactional
  public void flush() {
    entityManager.flush();
  }

  @Transactional
  public void clear() {
    entityManager.clear();
  }

  @Transactional
  public void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  @Transactional
  public <TEntity> List<TEntity> reattach(List<TEntity> entities) {
    return entities.stream().map(entityManager::merge).toList();
  }

  public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
      List<TEntity> createAndPersistMultipleRandom(Class<TGenerator> generatorType, int count) {
    List<TEntity> entities = DataGenerator.createMultipleRandom(generatorType, count);
    if (entities.isEmpty()) {
      return entities;
    }
    return persist(generatorType, entities);
  }

  /**
   * Save an already-modified ApplicationEntity (e.g. after attaching a DecisionEntity). Delegates
   * to the ApplicationRepository without going through a generator.
   */
  @Transactional
  public ApplicationEntity saveApplication(ApplicationEntity application) {
    ApplicationRepository repo =
        (ApplicationRepository) applicationContext.getBean(ApplicationRepository.class);
    return repo.save(application);
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
    lead.addLinkedApplication(associated);
    ApplicationRepository repo = applicationContext.getBean(ApplicationRepository.class);
    repo.save(lead); // Changed from saveAndFlush - let batch handle flushing
  }

  /**
   * Links multiple applications in a batch for better performance. Each entry in the list is a pair
   * of [leadId, associatedId].
   *
   * @param linkPairs list of UUID pairs where first is lead and second is associated
   */
  @Transactional
  public void linkApplicationsBatch(java.util.List<java.util.List<UUID>> linkPairs) {
    ApplicationRepository repo = applicationContext.getBean(ApplicationRepository.class);

    for (java.util.List<UUID> pair : linkPairs) {
      UUID leadId = pair.get(0);
      UUID associatedId = pair.get(1);

      ApplicationEntity lead = entityManager.find(ApplicationEntity.class, leadId);
      ApplicationEntity associated = entityManager.find(ApplicationEntity.class, associatedId);
      lead.addLinkedApplication(associated);
      repo.save(lead);
    }

    // Flush all at once at the end of the batch
    entityManager.flush();
  }

  /**
   * Merge a DecisionEntity that already has an ID (e.g. after setting meritsDecisions
   * post-persist). Uses EntityManager.merge so that already-persisted MeritsDecisionEntity objects
   * are treated as managed rather than triggering a duplicate CascadeType.PERSIST.
   */
  @Transactional
  public DecisionEntity mergeDecision(DecisionEntity decision) {
    DecisionRepository repo =
        (DecisionRepository) applicationContext.getBean(DecisionRepository.class);
    return repo.saveAndFlush(entityManager.merge(decision));
  }
}
