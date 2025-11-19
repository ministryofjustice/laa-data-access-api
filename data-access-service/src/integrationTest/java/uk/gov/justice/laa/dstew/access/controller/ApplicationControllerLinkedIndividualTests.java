package uk.gov.justice.laa.dstew.access.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedIndividualEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

@SpringBootTest(classes = AccessApp.class)
@AutoConfigureMockMvc
@Transactional
public class ApplicationControllerLinkedIndividualTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Test
  @WithMockUser(authorities = "APPROLE_ApplicationReader")
  @Transactional
  void shouldReturnLinkedIndividuals() throws Exception {

    UUID appId = UUID.randomUUID();

    ApplicationEntity app = new ApplicationEntity();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setApplicationContent(Map.of("foo", "bar"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    IndividualEntity ind1 = new IndividualEntity();
    ind1.setId(UUID.randomUUID());
    ind1.setFirstName("John");
    ind1.setLastName("Doe");
    ind1.setDateOfBirth(LocalDate.of(1990, 1, 1));
    ind1.setIndividualContent(Map.of("email", "john.doe@example.com"));

    LinkedIndividualEntity link1 = new LinkedIndividualEntity();
    link1.setId(UUID.randomUUID());
    link1.setLinkedApplication(app);
    link1.setLinkedIndividual(ind1);

    IndividualEntity ind2 = new IndividualEntity();
    ind2.setId(UUID.randomUUID());
    ind2.setFirstName("Jane");
    ind2.setLastName("Doe");
    ind2.setDateOfBirth(LocalDate.of(1990, 1, 1));
    ind2.setIndividualContent(Map.of("email", "jane.doe@example.com"));

    LinkedIndividualEntity link2 = new LinkedIndividualEntity();
    link2.setId(UUID.randomUUID());
    link2.setLinkedApplication(app);
    link2.setLinkedIndividual(ind2);

    app.setLinkedIndividuals(Set.of(link1, link2));

    applicationRepository.saveAndFlush(app);

    mockMvc.perform(get("/api/v0/applications/" + appId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.linked_individuals.length()").value(2))
        .andExpect(jsonPath("$.linked_individuals[0].firstName").exists())
        .andExpect(jsonPath("$.linked_individuals[1].firstName").exists());
  }


  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  void shouldReturnEmptyLinkedIndividualsList() throws Exception {

    ApplicationEntity app = new ApplicationEntity();
    UUID appId = UUID.randomUUID();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setSchemaVersion(1);
    app.setApplicationContent(Map.of("first_name", "Alice", "last_name", "Wonder"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    app.setLinkedIndividuals(Set.of());
    applicationRepository.save(app);

    mockMvc.perform(get("/api/v0/applications/" + appId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.linked_individuals").isArray())
        .andExpect(jsonPath("$.linked_individuals").isEmpty());
  }
}
