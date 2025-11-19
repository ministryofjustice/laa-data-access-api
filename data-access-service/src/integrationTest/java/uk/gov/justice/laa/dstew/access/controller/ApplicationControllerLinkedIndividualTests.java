package uk.gov.justice.laa.dstew.access.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  void shouldReturnLinkedIndividuals() throws Exception {

    // Create ApplicationEntity with manually assigned ID
    ApplicationEntity app = new ApplicationEntity();
    UUID appId = UUID.randomUUID();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setSchemaVersion(1);
    app.setApplicationContent(Map.of("first_name", "John", "last_name", "Doe"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    // Create IndividualEntities
    IndividualEntity ind1 = new IndividualEntity();
    ind1.setFirstName("John");
    ind1.setLastName("Doe");
    ind1.setDateOfBirth(LocalDate.parse("1990-01-01"));

    IndividualEntity ind2 = new IndividualEntity();
    ind2.setFirstName("Jane");
    ind2.setLastName("Doe");
    ind2.setDateOfBirth(LocalDate.parse("1992-02-02"));

    // Create LinkedIndividualEntities
    LinkedIndividualEntity li1 = new LinkedIndividualEntity();
    li1.setLinkedApplication(app);
    li1.setLinkedIndividual(ind1);

    LinkedIndividualEntity li2 = new LinkedIndividualEntity();
    li2.setLinkedApplication(app);
    li2.setLinkedIndividual(ind2);

    // Set linked individuals on application
    app.setLinkedIndividuals(Set.of(li1, li2));

    // Persist application (cascades linked individuals)
    applicationRepository.save(app);

    // Perform GET
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications/" + appId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.linked_individuals").isArray())
        .andExpect(jsonPath("$.linked_individuals").isNotEmpty())
        .andExpect(jsonPath("$.linked_individuals.length()").value(2))
        .andExpect(jsonPath("$.linked_individuals[0].firstName").value("John"))
        .andExpect(jsonPath("$.linked_individuals[1].firstName").value("Jane"));
  }

  @Test
  @WithMockUser(authorities = {"APPROLE_ApplicationReader", "APPROLE_ApplicationWriter"})
  void shouldReturnEmptyLinkedIndividualsList() throws Exception {

    // Create ApplicationEntity without linked individuals
    ApplicationEntity app = new ApplicationEntity();
    UUID appId = UUID.randomUUID();
    app.setId(appId);
    app.setStatus(ApplicationStatus.SUBMITTED);
    app.setSchemaVersion(1);
    app.setApplicationContent(Map.of("first_name", "Alice", "last_name", "Wonder"));
    app.setCreatedAt(Instant.now());
    app.setModifiedAt(Instant.now());

    // No linked individuals
    app.setLinkedIndividuals(Set.of());

    applicationRepository.save(app);

    // Perform GET
    mockMvc.perform(MockMvcRequestBuilders.get("/api/v0/applications/" + appId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.linked_individuals").isArray())
        .andExpect(jsonPath("$.linked_individuals").isEmpty());
  }
}
