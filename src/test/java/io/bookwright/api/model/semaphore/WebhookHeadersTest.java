package io.bookwright.api.model.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookHeadersTest {

  @Test
  void diagnosticsDoNotExposeHeaderValues() {
    var headers = new WebhookHeaders(Map.of("X-Signature", "sensitive-signature"));

    assertThat(headers.toString()).contains("[REDACTED]").doesNotContain("sensitive-signature");
  }
}
