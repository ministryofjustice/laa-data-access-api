package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Component
public class ApplicationAsserts {

    @Autowired
    private ApplicationRepository applicationRepository;

    public void assertApplicationsMatchInRepository(List<ApplicationEntity> expected) {
        List<ApplicationEntity> actual = applicationRepository.findAllById(
                expected.stream().map(ApplicationEntity::getId).collect(Collectors.toList()));
        assertThat(expected.size()).isEqualTo(actual.size());
        assertTrue(expected.containsAll(actual));
    }
}
