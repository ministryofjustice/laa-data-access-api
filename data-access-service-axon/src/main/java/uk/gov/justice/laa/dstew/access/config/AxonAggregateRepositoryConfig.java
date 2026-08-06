package uk.gov.justice.laa.dstew.access.config;

import java.util.Optional;
import java.util.UUID;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.modelling.repository.Repository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemAggregate;

/**
 * Exposes Axon aggregate repositories as Spring beans.
 *
 * <p>In Axon 5 the Spring extension builds aggregate repositories inside isolated module
 * configurations — they do NOT exist in Spring's BeanFactory. This class bridges that gap by
 * retrieving each repository from the module-level {@link Configuration} and registering it as
 * a named Spring bean so it can be injected normally.
 *
 * <p>The lookup key follows Axon 5's {@code EntityModule.entityName()} contract:
 * {@code "<FQN entity class>#<FQN id class>"}.
 */
@org.springframework.context.annotation.Configuration
public class AxonAggregateRepositoryConfig {

  /**
   * The entity-name key Axon uses internally for the WorkItemAggregate repository component.
   * Format: {@code EntityModule.entityName()} = {@code "%s#%s".formatted(entityFqn, idFqn)}.
   */
  private static final String WORK_ITEM_ENTITY_NAME =
      WorkItemAggregate.class.getName() + "#" + UUID.class.getName();

  @Bean
  @Lazy
  @SuppressWarnings("unchecked")
  public Repository<UUID, WorkItemAggregate> workItemAggregateRepository(
      Configuration axonConfiguration) {
    return repositoryIn(axonConfiguration)
        .map(repository -> (Repository<UUID, WorkItemAggregate>) repository)
        .orElseThrow(() -> new IllegalStateException(
            "No Axon repository found for WorkItemAggregate. "
                + "Check that WorkItemAggregate is annotated with @EventSourced "
                + "and its module is registered."));
  }

  @SuppressWarnings("rawtypes")
  private Optional<Repository> repositoryIn(Configuration configuration) {
    Optional<Repository> repository =
        configuration.getOptionalComponent(Repository.class, WORK_ITEM_ENTITY_NAME);
    if (repository.isPresent()) {
      return repository;
    }

    return configuration.getModuleConfigurations().stream()
        .map(this::repositoryIn)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }
}

