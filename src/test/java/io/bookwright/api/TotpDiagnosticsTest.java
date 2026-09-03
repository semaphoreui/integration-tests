package io.bookwright.api;

import io.bookwright.api.model.semaphore.TotpPasscodeRequest;
import io.bookwright.api.model.semaphore.TotpRecoveryRequest;
import io.bookwright.api.model.semaphore.UserTotp;
import org.junit.jupiter.api.Test;

class TotpDiagnosticsTest {

  @Test
  void otpModelsDoNotExposeTheirSecrets() {
    assertRedacted(new TotpPasscodeRequest("123456"), "123456");
    assertRedacted(new TotpRecoveryRequest("recovery-value"), "recovery-value");
    assertRedacted(
        new UserTotp(1, 2, "otpauth://totp/test?secret=base32-value", "recovery-value"),
        "base32-value",
        "recovery-value");
  }

  private void assertRedacted(Object diagnostic, String... secrets) {
    String value = diagnostic.toString();
    for (String secret : secrets) {
      if (value.contains(secret)) {
        throw new AssertionError("TOTP diagnostic exposed sensitive material");
      }
    }
  }
}
