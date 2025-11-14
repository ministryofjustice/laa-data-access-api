package uk.gov.justice.laa.dstew.access.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts;
import uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import uk.gov.justice.laa.dstew.access.utils.factory.PersistedFactory;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = AccessApp.class, properties = "feature.disable-security=true")
public abstract class BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    public static String jdbcUrl;
    private static String jdbcUsername;
    private static String jdbcPassword;

//    @Container
//    @ServiceConnection
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//    }

    @Autowired
    protected ApplicationRepository applicationRepository;

    @Autowired
    protected Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> applicationFactory;

    @Autowired
    protected PersistedFactory<
            ApplicationRepository,
            Factory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder>,
            ApplicationEntity,
            ApplicationEntity.ApplicationEntityBuilder,
            UUID> persistedApplicationFactory;

    @BeforeEach
    void clearRepositories() {
        applicationRepository.deleteAll();
    }

    public MvcResult getUri(String uri) throws Exception {
        return mockMvc
            .perform(get(uri))
            .andReturn();
    }

    public MvcResult getUri(String uri, Object... args) throws Exception {
        return mockMvc
                .perform(get(uri, args))
                .andReturn();
    }

    public <TResponseModel> TResponseModel deserialise(MvcResult result, Class<TResponseModel> clazz) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
    }
}