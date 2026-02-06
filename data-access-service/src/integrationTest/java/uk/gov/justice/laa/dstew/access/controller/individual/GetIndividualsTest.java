package uk.gov.justice.laa.dstew.access.controller.individual;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;


import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
public class GetIndividualsTest extends BaseIntegrationTest {

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenExistingIndividual_whenGetIndividuals_thenReturnOKWithCorrectData() throws Exception {
    // given
    IndividualEntity persisted = persistedIndividualFactory.createAndPersist();

    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    assertThat(response.getIndividuals()).isNotEmpty();
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(1);
    assertThat(response.getPaging().getPage()).isEqualTo(1);
    assertThat(response.getPaging().getPageSize()).isEqualTo(10);

    // Verify the individual in the response matches the persisted entity
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().get(0).getFirstName()).isEqualTo(persisted.getFirstName());
    assertThat(response.getIndividuals().get(0).getLastName()).isEqualTo(persisted.getLastName());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
  public void givenUnknownRole_whenGetIndividuals_thenReturnForbidden() throws Exception {
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);

    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenNoUser_whenGetIndividuals_thenReturnUnauthorised() throws Exception {
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);

    // then
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenPageSizeGreaterThan100_whenGetIndividuals_thenCapAt100() throws Exception {
    // given - create more than 100 individuals
    for (int i = 0; i < 105; i++) {
      persistedIndividualFactory.createAndPersist();
    }

    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=1&pageSize=150");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertOK(result);
    assertThat(response.getPaging().getPageSize()).isEqualTo(100); // Capped at maximum
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(100);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenNegativePage_whenGetIndividuals_thenDefaultToPage1() throws Exception {
    // given
    persistedIndividualFactory.createAndPersist();

    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=-1&pageSize=10");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertOK(result);
    assertThat(response.getPaging().getPage()).isEqualTo(1); // Defaults to 1
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenPageSizeLessThan1_whenGetIndividuals_thenDefaultTo10() throws Exception {
    // given
    persistedIndividualFactory.createAndPersist();

    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=1&pageSize=0");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertOK(result);
    assertThat(response.getPaging().getPageSize()).isEqualTo(10); // Defaults to 10
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenEmptyDatabase_whenGetIndividuals_thenReturnEmptyList() throws Exception {
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=1&pageSize=10");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertOK(result);
    assertThat(response.getIndividuals()).isEmpty();
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(0);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(0);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  public void givenPageBeyondAvailableData_whenGetIndividuals_thenReturnEmptyList() throws Exception {
    // given
    persistedIndividualFactory.createAndPersist();

    // when - request page 100 when only 1 record exists
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=100&pageSize=10");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);

    // then
    assertOK(result);
    assertThat(response.getIndividuals()).isEmpty();
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(0);
    assertThat(response.getPaging().getPage()).isEqualTo(100);
  }
}
