package uk.gov.justice.laa.dstew.access.controller.individual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class GetIndividualsTest extends BaseIntegrationTest {

  @ParameterizedTest
  @MethodSource("pagingParameters")
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenPagingParameters_whenGetIndividuals_thenCorrectPagingInResponse(Integer page, Integer pageSize, int expectedPage, int expectedPageSize, int totalEntities, int expectedReturned, int expectedTotalRecords) throws Exception {
    // given
    persistedIndividualFactory.createAndPersistMultiple(totalEntities, builder -> {});
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page="+ page + "&pageSize=" + pageSize);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getPaging().getPage()).isEqualTo(expectedPage);
    assertThat(response.getPaging().getPageSize()).isEqualTo(expectedPageSize);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(expectedReturned);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(expectedTotalRecords);
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> pagingParameters() {
    return Stream.of(
        // page, pageSize, expectedPage, expectedPageSize, totalEntities, expectedReturned, expectedTotalRecords
        org.junit.jupiter.params.provider.Arguments.of(1, 10, 1, 10, 15, 10, 15), // first page, 10 of 15
        org.junit.jupiter.params.provider.Arguments.of(2, 10, 2, 10, 15, 5, 15), // second page, 5 of 15
        org.junit.jupiter.params.provider.Arguments.of(-1, 10, 1, 10, 5, 5, 5), // negative page, default to 1
        org.junit.jupiter.params.provider.Arguments.of(1, 0, 1, 10, 5, 5, 5), // zero pageSize, default to 10
        org.junit.jupiter.params.provider.Arguments.of(1, 101, 1, 100, 150, 100, 150), // pageSize > 100 capped
        org.junit.jupiter.params.provider.Arguments.of(100, 10, 100, 10, 15, 0, 15) // page beyond data, empty
    );
  }

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
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().get(0).getFirstName()).isEqualTo(persisted.getFirstName());
    assertThat(response.getIndividuals().get(0).getLastName()).isEqualTo(persisted.getLastName());
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
  public void givenUnknownRole_whenGetIndividuals_thenReturnForbiddenResponse() throws Exception {
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenNoUser_whenGetIndividuals_thenReturnUnauthorisedResponse() throws Exception {
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    // then
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  @WithMockUser(authorities = TestConstants.Roles.READER)
  void givenNullPageAndPageSize_whenGetIndividuals_thenDefaultsAreApplied() throws Exception {
    // given
    persistedIndividualFactory.createAndPersistMultiple(5, builder -> {});
    // when
    MvcResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getPaging().getPage()).isEqualTo(1);
    assertThat(response.getPaging().getPageSize()).isEqualTo(10);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(5);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(5);
  }
}
