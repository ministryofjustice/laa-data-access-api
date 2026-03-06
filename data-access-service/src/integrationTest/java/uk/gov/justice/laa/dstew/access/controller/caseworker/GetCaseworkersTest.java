package uk.gov.justice.laa.dstew.access.controller.caseworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
public class GetCaseworkersTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    void givenRoleReaderAndNoHeader_whenGetCaseworkers_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenRoleReaderAndIncorrectHeader_whenGetCaseworkers_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        MvcResult result = getUri(TestConstants.URIs.GET_CASEWORKERS, ServiceNameHeader(serviceName));

        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.CASEWORKER)
    public void givenRoleReader_whenGetCaseworkers_thenReturnOk() throws Exception {
        // given
        // two caseworkers created in BaseIntegrationTest data setup.

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);
        List<Caseworker> actualCaseworkers = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Caseworker>>() {}
        );

        // then
        assertSecurityHeaders(result);
        assertOK(result);
        assertCaseworkerListEquals(actualCaseworkers, Caseworkers);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
    public void givenUnknownRole_whenGetCaseworkers_thenReturnForbidden() throws Exception {
        // given
        // when
        MvcResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetCaseworkers_thenReturnUnauthorised() throws Exception {
        // given
        // when
        MvcResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    private void assertCaseworkerListEquals(List<Caseworker> caseworkers, List<CaseworkerEntity> entities) {
        assertThat(caseworkers).hasSameSizeAs(entities);
        for (int i = 0; i < caseworkers.size(); i++) {
            assertCaseworkerEquals(caseworkers.get(i), entities.get(i));
        }
    }

    private void assertCaseworkerEquals(Caseworker caseworker, CaseworkerEntity entity) {
        assertThat(caseworker.getId()).isEqualTo(entity.getId());
        assertThat(caseworker.getUsername()).isEqualTo(entity.getUsername());
    }
}
