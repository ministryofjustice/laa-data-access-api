package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficeCodeResourceWriteAccessResolverTest {
  private final OfficeCodeResourceWriteAccessResolver resolver =
      new OfficeCodeResourceWriteAccessResolver();

  @Test
  void resolvesTheConfiguredResourceIdentityAndOfficeCode() throws Exception {
    UUID priorAuthorityId = UUID.randomUUID();
    Method method = Handler.class.getDeclaredMethod("handle", Resource.class);
    AggregateResource metadata = method.getParameters()[0].getAnnotation(AggregateResource.class);

    var targets =
        resolver.resolve(
            new Object(),
            new WriteAccessTargetResolutionContext(
                method, new Resource(priorAuthorityId, "0Z123A"), metadata));

    assertThat(targets)
        .containsExactly(
            new OfficeCodeWriteAccessTarget("prior-authority", priorAuthorityId, "0Z123A"));
  }

  @Test
  void rejectsResourcesWhoseConfiguredIdentifierIsNotAUuid() throws Exception {
    Method method =
        InvalidIdentifierHandler.class.getDeclaredMethod("handle", InvalidResource.class);
    AggregateResource metadata = method.getParameters()[0].getAnnotation(AggregateResource.class);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Object(),
                    new WriteAccessTargetResolutionContext(
                        method, new InvalidResource("not-a-uuid", "0Z123A"), metadata)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("priorAuthorityId property must be a UUID");
  }

  @Test
  void rejectsResourcesWithoutTheConfiguredIdentifierProperty() throws Exception {
    Method method =
        MissingIdentifierHandler.class.getDeclaredMethod("handle", MissingIdentifier.class);
    AggregateResource metadata = method.getParameters()[0].getAnnotation(AggregateResource.class);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Object(),
                    new WriteAccessTargetResolutionContext(
                        method, new MissingIdentifier("0Z123A"), metadata)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readable priorAuthorityId property");
  }

  @Test
  void rejectsResourcesWithoutAnOfficeCodeProperty() throws Exception {
    Method method =
        MissingOfficeCodeHandler.class.getDeclaredMethod("handle", MissingOfficeCode.class);
    AggregateResource metadata = method.getParameters()[0].getAnnotation(AggregateResource.class);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Object(),
                    new WriteAccessTargetResolutionContext(
                        method, new MissingOfficeCode(UUID.randomUUID()), metadata)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readable officeCode property");
  }

  @Test
  void rejectsResourcesWhoseOfficeCodeIsNotAString() throws Exception {
    Method method =
        InvalidOfficeCodeHandler.class.getDeclaredMethod("handle", InvalidOfficeCode.class);
    AggregateResource metadata = method.getParameters()[0].getAnnotation(AggregateResource.class);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new Object(),
                    new WriteAccessTargetResolutionContext(
                        method, new InvalidOfficeCode(UUID.randomUUID(), 123), metadata)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("officeCode property must be a String");
  }

  private static class Handler {
    void handle(
        @AggregateResource(
                resourceType = "prior-authority",
                resourceIdProperty = "priorAuthorityId")
            Resource resource) {}
  }

  private static class InvalidIdentifierHandler {
    void handle(
        @AggregateResource(
                resourceType = "prior-authority",
                resourceIdProperty = "priorAuthorityId")
            InvalidResource resource) {}
  }

  private static class MissingIdentifierHandler {
    void handle(
        @AggregateResource(
                resourceType = "prior-authority",
                resourceIdProperty = "priorAuthorityId")
            MissingIdentifier resource) {}
  }

  private static class MissingOfficeCodeHandler {
    void handle(
        @AggregateResource(
                resourceType = "prior-authority",
                resourceIdProperty = "priorAuthorityId")
            MissingOfficeCode resource) {}
  }

  private static class InvalidOfficeCodeHandler {
    void handle(
        @AggregateResource(
                resourceType = "prior-authority",
                resourceIdProperty = "priorAuthorityId")
            InvalidOfficeCode resource) {}
  }

  private record Resource(UUID priorAuthorityId, String officeCode) {}

  private record InvalidResource(String priorAuthorityId, String officeCode) {}

  private record MissingIdentifier(String officeCode) {}

  private record MissingOfficeCode(UUID priorAuthorityId) {}

  private record InvalidOfficeCode(UUID priorAuthorityId, int officeCode) {}
}
