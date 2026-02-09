package uk.gov.justice.laa.dstew.access.service.individual;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.utils.BaseServiceTest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class GetIndividualsTest extends BaseServiceTest {

    @Autowired
    private IndividualsService individualsService;

    @Test
    void whenNoIndividuals_thenEmptyResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<IndividualEntity> entityPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(individualRepository, times(1)).findAll(pageable);
    }

    @Test
    void whenIndividualsExist_thenReturnPagedResponse() {
        IndividualEntity entity1 = individualEntityFactory.createDefault();
        IndividualEntity entity2 = individualEntityFactory.createDefault();
        Pageable pageable = PageRequest.of(0, 2);
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity1, entity2), pageable, 3);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(0, 2);

        assertThat(result.getContent().size()).isEqualTo(2);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        verify(individualRepository, times(1)).findAll(pageable);
    }

    @Test
    void whenRequestSecondPage_thenReturnRemainingIndividuals() {
        IndividualEntity entity = individualEntityFactory.createDefault();
        Pageable pageable = PageRequest.of(1, 2);
        Page<IndividualEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 3);
        when(individualRepository.findAll(pageable)).thenReturn(entityPage);

        Page<Individual> result = individualsService.getIndividuals(1, 2);

        assertThat(result.getContent().size()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        verify(individualRepository, times(1)).findAll(pageable);
    }
}
