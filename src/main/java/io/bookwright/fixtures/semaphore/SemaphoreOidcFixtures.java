package io.bookwright.fixtures.semaphore;

/** Local-only Dex identity and expectations for the Semaphore OIDC feature profile. */
public record SemaphoreOidcFixtures(
    String providerName, String username, String email, String password, String returnPath) {

  public static SemaphoreOidcFixtures standard() {
    return new SemaphoreOidcFixtures(
        "Bookwright Dex",
        "oidc.user@bookwright.test",
        "oidc.user@bookwright.test",
        "Bookwright-OIDC-42!",
        "/tokens");
  }

  @Override
  public String toString() {
    return "SemaphoreOidcFixtures[providerName=%s, username=%s, email=%s, password=[REDACTED], returnPath=%s]"
        .formatted(providerName, username, email, returnPath);
  }
}
