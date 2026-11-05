package io.bookwright.api;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretSanitizerUrlTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "code",
        "state",
        "nonce",
        "session_state",
        "code_verifier",
        "code_challenge",
        "client_assertion",
        "SAMLRequest",
        "SAMLResponse",
        "RelayState"
      })
  void redactsAuthenticationQueryParameters(String parameter) {
    HttpUrl original =
        HttpUrl.get("https://example.test/semaphore/api/auth/oidc/dex/redirect")
            .newBuilder()
            .addQueryParameter(parameter, "auth-material")
            .addQueryParameter("view", "compact")
            .build();

    assertThat(SecretSanitizer.url(original))
        .contains("/semaphore/api/auth/oidc/dex/redirect", "view=compact", "%5BREDACTED%5D")
        .doesNotContain("auth-material");
    assertThat(original.queryParameter(parameter)).isEqualTo("auth-material");
  }

  @Test
  void redactsEncodedCaseInsensitiveAndRepeatedNames() {
    assertThat(
            SecretSanitizer.url(
                "https://example.test/callback?%63ode=first-code&code=second-code&StAtE=state-value&%74oken=encoded-token&view=compact"))
        .contains("view=compact", "code=%5BREDACTED%5D", "StAtE=%5BREDACTED%5D")
        .doesNotContain("first-code", "second-code", "state-value", "encoded-token");
  }

  @Test
  void removesUserInfoAndTheWholeFragmentWithoutChangingTheRequestUrl() {
    HttpUrl original =
        HttpUrl.get(
            "https://fixture-user:fixture-password@example.test:8443/callback?view=compact#access_token=fragment-secret");

    assertThat(SecretSanitizer.url(original))
        .isEqualTo("https://example.test:8443/callback?view=compact");
    assertThat(original.password()).isEqualTo("fixture-password");
    assertThat(original.fragment()).isEqualTo("access_token=fragment-secret");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"not a URL?code=secret", "https://[invalid?state=secret"})
  void omitsUnparseableUrls(String value) {
    assertThat(SecretSanitizer.url(value)).isEqualTo("[URL OMITTED]");
  }

  @Test
  void queryOnlyNamesDoNotHideOrdinaryJsonStateOrCodeFields() {
    assertThat(
            SecretSanitizer.body(
                "{\"state\":\"waiting\",\"code\":409}", MediaType.get("application/json")))
        .isEqualTo("{\"state\":\"waiting\",\"code\":409}");
  }
}
