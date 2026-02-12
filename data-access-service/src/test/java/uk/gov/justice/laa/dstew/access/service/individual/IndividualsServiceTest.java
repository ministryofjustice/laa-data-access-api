package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndividualsServiceTest extends BaseServiceTest {

    @Autowired
    private IndividualsService individualsService;

    @Autowired
    private IndividualEntityFactory individualEntityFactory;

    @Test
    @SuppressWarnings("unchecked")
    void noFilters_whenGetIndividuals_thenRepositoryFindAllWithPageable() {
        setSecurityContext(TestConstants.Roles.READER);
        IndividualEntity entity = individualEntityFactory.createDefault();
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
        when(individualRepository.findAll(any(Pageable.class))).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10, null, null);

        verify(individualRepository, times(1)).findAll(any(Pageable.class));
        verify(individualRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationIdProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
        setSecurityContext(TestConstants.Roles.READER);
        UUID appId = UUID.randomUUID();
        IndividualEntity entity = individualEntityFactory.createDefault();
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
        when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10, appId, null);

        verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(individualRepository, never()).findAll(any(Pageable.class));
        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void individualTypeProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
        setSecurityContext(TestConstants.Roles.READER);
        IndividualType type = IndividualType.CLIENT;
        IndividualEntity entity = individualEntityFactory.createDefault();
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
        when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10, null, type);

        verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(individualRepository, never()).findAll(any(Pageable.class));
        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothFiltersProvided_whenGetIndividuals_thenRepositoryFindAllWithSpecificationAndPageable() {
        setSecurityContext(TestConstants.Roles.READER);
        UUID appId = UUID.randomUUID();
        IndividualType type = IndividualType.CLIENT;
        IndividualEntity entity = individualEntityFactory.createDefault();
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity));
        when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10, appId, type);

        verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(individualRepository, never()).findAll(any(Pageable.class));
        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void noMatchingData_whenGetIndividuals_thenReturnsEmptyPage() {
        setSecurityContext(TestConstants.Roles.READER);
        UUID appId = UUID.randomUUID();
        IndividualType type = IndividualType.CLIENT;
        when(individualRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        Page<Individual> result = individualsService.getIndividuals(0, 10, appId, type);

        assertThat(result).isEmpty();
        verify(individualRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void matchingData_whenGetIndividuals_thenMapperConvertsEntitiesToModels() {
        setSecurityContext(TestConstants.Roles.READER);
        IndividualEntity entity = individualEntityFactory.createDefault();
        List<IndividualEntity> entities = List.of(entity);
        Page<IndividualEntity> entityPage = new PageImpl<>(entities);

        when(individualRepository.findAll(any(Pageable.class))).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getFirstName()).isEqualTo(entity.getFirstName());
        assertThat(result.getContent().getFirst().getLastName()).isEqualTo(entity.getLastName());
        verify(individualRepository, times(1)).findAll(any(Pageable.class));
    }
}
