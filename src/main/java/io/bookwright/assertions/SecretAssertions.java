package io.bookwright.assertions;

import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import lombok.experimental.UtilityClass;

/** Fails without echoing the forbidden secret into reports or exception messages. */
@UtilityClass
public class SecretAssertions {

  public void absent(String surface, String content, SecretAccessKey secret) {
    if (content != null && content.contains(secret.password())) {
      throw new AssertionError("Sensitive access-key password was exposed in " + surface);
    }
  }
}
