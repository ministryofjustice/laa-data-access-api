package uk.gov.justice.laa.dstew.access.controller.caseworker;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

public class GetCaseworkersTest extends BaseHarnessTest {

    @SmokeTest
    @Test
    void givenRoleReaderAndNoHeader_whenGetCaseworkers_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    @SmokeTest
    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenRoleReaderAndIncorrectHeader_whenGetCaseworkers_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS, ServiceNameHeader(serviceName));
        assertBadRequest(result);
    }

    @SmokeTest
    @Test
    public void givenRoleReader_whenGetCaseworkers_thenReturnOk() throws Exception {
        // given
        // two caseworkers created in BaseHarnessTest data setup.

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);
        List<Caseworker> actualCaseworkers = objectMapper.readValue(
                result.getResponse().getContentAsString(), objectMapper.getTypeFactory().constructCollectionType(List.class, Caseworker.class)
        );

        // then
        assertSecurityHeaders(result);
        assertOK(result);
        assertCaseworkerListEquals(actualCaseworkers, Caseworkers);
    }

    @Test
    public void givenUnknownRole_whenGetCaseworkers_thenReturnForbidden() throws Exception {
        withToken(TestConstants.Tokens.UNKNOWN);
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetCaseworkers_thenReturnUnauthorised() throws Exception {
        withNoToken();
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    private void assertCaseworkerListEquals(List<Caseworker> caseworkers, List<CaseworkerEntity> entities) {
        for (CaseworkerEntity entity : entities) {
            assertThat(caseworkers).anyMatch(c ->
                    entity.getId().equals(c.getId()) &&
                    entity.getUsername().equals(c.getUsername()));
        }
    }
}
