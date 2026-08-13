package io.bookwright.assertions;

import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import lombok.experimental.UtilityClass;

/** Fails without echoing the forbidden secret into reports or exception messages. */
@UtilityClass
public class SecretAssertions {

  public void absent(String surface, String content, SecretAccessKey secret) {
    absent(surface, content, secret.password());
  }

  public void absent(String surface, String content, String sensitiveValue) {
    if (content != null && content.contains(sensitiveValue)) {
      throw new AssertionError("Sensitive value was exposed in " + surface);
    }
  }

  public void credentialsAbsent(String surface, String content, SecretAccessKey secret) {
    absent(surface, content, secret);
    if (content != null && content.contains(secret.login())) {
      throw new AssertionError("Sensitive access-key login was exposed in " + surface);
    }
  }
}
