package uk.gov.justice.laa.dstew.access.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.mapper.IndividualMapper;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class IndividualsServiceTest {

    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private IndividualMapper individualMapper;
    @InjectMocks
    private IndividualsService individualsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void whenNoIndividuals_thenEmptyResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<IndividualEntity> entityPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    void whenIndividualsExist_thenReturnPagedResponse() {
        IndividualEntity entity1 = new IndividualEntity();
        IndividualEntity entity2 = new IndividualEntity();
        Individual individual1 = new Individual();
        Individual individual2 = new Individual();
        Pageable pageable = PageRequest.of(0, 2);
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity1, entity2), pageable, 3);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);
        when(individualMapper.toIndividual(entity1)).thenReturn(individual1);
        when(individualMapper.toIndividual(entity2)).thenReturn(individual2);

        Page<Individual> result = individualsService.getIndividuals(0, 2);

        assertThat(result.getContent().size()).isEqualTo(2);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void whenRequestSecondPage_thenReturnRemainingIndividuals() {
        IndividualEntity entity = new IndividualEntity();
        Individual individual = new Individual();
        Pageable pageable = PageRequest.of(1, 2);
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 3);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);
        when(individualMapper.toIndividual(entity)).thenReturn(individual);

        Page<Individual> result = individualsService.getIndividuals(1, 2);

        assertThat(result.getContent().size()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}
