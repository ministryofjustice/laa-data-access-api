package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.Constants.POSTGRES_INSTANCE;

import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

@Testcontainers
@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ApplicationRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_INSTANCE);

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    CaseworkerRepository caseworkerRepository;

    final static int NUMBER_OF_PREPOPULATED_APPLICATIONS = 5;
    List<ApplicationEntity> prePopulatedApplications;

    @BeforeEach
    void setUp() throws Exception {
        final CaseworkerEntity persistedCaseworker = caseworkerRepository.save(CaseworkerEntity.builder().username("caseworker_1").build());
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");
        prePopulatedApplications = Instancio.ofList(ApplicationEntity.class)
                                .size(NUMBER_OF_PREPOPULATED_APPLICATIONS)
                                .set(Select.field(ApplicationEntity::getIndividuals), Set.of())
                                .set(Select.field(ApplicationEntity::getApplicationContent), map)
                                .set(Select.field(ApplicationEntity::getId), null)
                                .generate(Select.field(ApplicationEntity::getCaseworker), gen -> gen.oneOf(null, persistedCaseworker))
                                .create();
        applicationRepository.saveAll(prePopulatedApplications);
    }

    @Test
    void applicationSave() {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("key", "value");
        var entity = ApplicationEntity.builder()
                                             .applicationContent(map)
                                             .status(ApplicationStatus.IN_PROGRESS)
                                             .build();
        applicationRepository.save(entity);
    }

    @Test
    void applicationGet() {
        var expectedEntity = prePopulatedApplications.get(0);
        Optional<ApplicationEntity> optionEntity = applicationRepository.findById(expectedEntity.getId());
        ApplicationEntity actualEntity = optionEntity.orElseThrow();
        assertApplicationEntitysAreEqual(expectedEntity, actualEntity);
    }

    @Test
    void applicationGetAll() {
        var applications = applicationRepository.findAll();
        assertEquals(NUMBER_OF_PREPOPULATED_APPLICATIONS, applications.size());
    }

    private static void assertApplicationEntitysAreEqual(ApplicationEntity x, ApplicationEntity y) {
        assertThat(x)
        .usingRecursiveComparison()
        .isEqualTo(y);
    }
}
