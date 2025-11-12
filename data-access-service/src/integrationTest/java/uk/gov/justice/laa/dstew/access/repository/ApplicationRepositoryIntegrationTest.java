package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.instancio.Instancio;
import org.instancio.Select;
import org.instancio.generator.specs.OneOfArrayGeneratorSpec;
import org.instancio.generators.Generators;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.StatusCodeLookupEntity;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;

import static uk.gov.justice.laa.dstew.access.Constants.POSTGRES_INSTANCE;

@Testcontainers
@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(statements = { "INSERT INTO public.status_code_lookup(id, code, description) VALUES ('5916ec11-b884-421e-907c-353618fc5b1c', 'pending', 'pending');",
                    "INSERT INTO public.status_code_lookup(id, code, description) VALUES ('de31c50b-c731-4df4-aaa4-6acf4f4d8fe3', 'accepted', 'accepted');"
                  })
public class ApplicationRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_INSTANCE);

    @Autowired
    ApplicationRepository applicationRepository;

    final static int NUMBER_OF_PREPOPULATED_APPLICATIONS = 5;
    List<ApplicationEntity> prePopulatedApplications;

    @BeforeEach
    void setUp() throws Exception {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("first_name", "jimi");
        map.put("last_name", "hendrix");
        prePopulatedApplications = Instancio.ofList(ApplicationEntity.class)
                                .size(NUMBER_OF_PREPOPULATED_APPLICATIONS)
                                .generate(Select.field(ApplicationEntity::getStatusEntity), gen -> StatusCodes(gen))
                                .set(Select.field(ApplicationEntity::getApplicationContent), map)
                                .create();
        applicationRepository.saveAll(prePopulatedApplications);
    }

    @Test
    void applicationSave() {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("key", "value");
        var entity = ApplicationEntity.builder()
                                             .id(UUID.randomUUID())
                                             .applicationContent(map)
                                             .build();
        entity.setStatusId(UUID.fromString("5916ec11-b884-421e-907c-353618fc5b1c"));
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

    static OneOfArrayGeneratorSpec<StatusCodeLookupEntity> StatusCodes(Generators gen) {
        StatusCodeLookupEntity pendingStatus = StatusCodeLookupEntity.builder()
                                                                     .code("pending")
                                                                     .id(UUID.fromString("5916ec11-b884-421e-907c-353618fc5b1c"))
                                                                     .build();
        StatusCodeLookupEntity acceptedStatus = StatusCodeLookupEntity.builder()
                                                                     .code("accepted")
                                                                     .id(UUID.fromString("de31c50b-c731-4df4-aaa4-6acf4f4d8fe3"))
                                                                     .build();
        return gen.oneOf(pendingStatus, acceptedStatus);
    }

    private static void assertApplicationEntitysAreEqual(ApplicationEntity x, ApplicationEntity y) {
        assertEquals(x.getId(), y.getId());
        assertEquals(x.getApplicationContent(), y.getApplicationContent());
        assertEquals(x.getCreatedAt(), y.getCreatedAt());
        assertEquals(x.getModifiedAt(), y.getModifiedAt());
        assertEquals(x.getSchemaVersion(), y.getSchemaVersion());
        assertEquals(x.getCreatedBy(), y.getCreatedBy());
        assertEquals(x.getStatusEntity().getCode(), y.getStatusEntity().getCode());
        assertEquals(x.getStatusEntity().getId(), y.getStatusEntity().getId());
    }
}
