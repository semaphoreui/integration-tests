package io.bookwright.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpCodesTest {

  private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
  private static final String URL = "otpauth://totp/Bookwright:test?secret=" + RFC_SECRET;

  @Test
  void generatesRfc6238Sha1CodesTruncatedToSixDigits() {
    assertThat(TotpCodes.at(URL, Instant.ofEpochSecond(59))).isEqualTo("287082");
    assertThat(TotpCodes.at(URL, Instant.ofEpochSecond(1_111_111_109))).isEqualTo("081804");
  }

  @Test
  void createsAValidButDifferentNegativePasscode() {
    assertThat(TotpCodes.differentFrom("123456")).isEqualTo("123450");
    assertThat(TotpCodes.differentFrom("123450")).isEqualTo("123451");
  }

  @Test
  void rejectsEnrollmentWithoutASecret() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TotpCodes.current("otpauth://totp/Bookwright:test"))
        .withMessageContaining("query data");
  }
}
