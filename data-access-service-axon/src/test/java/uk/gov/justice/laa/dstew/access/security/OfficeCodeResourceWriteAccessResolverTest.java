package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OfficeCodeResourceWriteAccessResolverTest {
  private final OfficeCodeResourceWriteAccessResolver resolver =
      new OfficeCodeResourceWriteAccessResolver();

  @Test
  void givenOfficeCodeOwnedResource_whenResolving_thenReturnsProtectedResource() {
    assertThat(resolver.resolve(new Object(), new Resource(" 0Z123A ")))
        .containsExactly(new ProtectedResource(" 0Z123A "));
  }

  @Test
  void givenResourceNotImplementingOfficeCodeOwned_whenResolving_thenFailsClearly() {
    assertThatThrownBy(() -> resolver.resolve(new Object(), new Object()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must implement OfficeCodeOwned");
  }

  private record Resource(String officeCode) implements OfficeCodeOwned {
    @Override
    public String getOfficeCode() {
      return officeCode;
    }
  }
}
