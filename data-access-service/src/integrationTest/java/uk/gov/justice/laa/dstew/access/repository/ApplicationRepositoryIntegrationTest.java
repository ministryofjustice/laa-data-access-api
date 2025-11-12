package uk.gov.justice.laa.dstew.access.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

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

    @Test
    void applicationSave() {
        ApplicationEntity entity = new ApplicationEntity();
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("key", "value");
        entity.setId(UUID.randomUUID());
        entity.setApplicationContent(map);
        entity.setStatusId(UUID.fromString("5916ec11-b884-421e-907c-353618fc5b1c"));
        applicationRepository.save(entity);
    }

    @Test
    @Sql(statements = { "INSERT INTO public.application(id, status_id, application_content, schema_version, application_reference)" + 
                        "VALUES ('019a773c-440f-7845-a538-b989fc89290b', '5916ec11-b884-421e-907c-353618fc5b1c', '{ \"first_name\" : \"jimi\" }', 1969, 'ref1');"})
    void applicationGet() throws Exception{
        Optional<ApplicationEntity> optionEntity = applicationRepository.findById(UUID.fromString("019a773c-440f-7845-a538-b989fc89290b"));
        ApplicationEntity entity = optionEntity.orElseThrow(); 
        assertEquals("019a773c-440f-7845-a538-b989fc89290b", entity.getId().toString());
        assertEquals(1969, entity.getSchemaVersion().intValue());
        assertEquals("5916ec11-b884-421e-907c-353618fc5b1c'", entity.getStatusEntity().getId().toString());
        assertEquals("pending", entity.getStatusEntity().getCode());
        assertEquals("{\"first_name\" : \"jimi\" }", entity.getApplicationContent());
    }
}
