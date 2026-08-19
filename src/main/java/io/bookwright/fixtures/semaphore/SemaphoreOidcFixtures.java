package io.bookwright.fixtures.semaphore;

/** Local-only Dex identities and expectations for the Semaphore OIDC feature profile. */
public record SemaphoreOidcFixtures(
    Login successfulLogin, Login localEmailConflict, Provider unavailableProvider) {

  public static SemaphoreOidcFixtures standard() {
    return new SemaphoreOidcFixtures(
        new Login(
            "Bookwright Dex",
            new Account(
                "oidc.user@bookwright.test", "oidc.user@bookwright.test", "Bookwright-OIDC-42!"),
            "/tokens"),
        new Login(
            "Bookwright Dex",
            new Account("admin@localhost", "admin@localhost", "Bookwright-OIDC-Conflict-42!"),
            "/tokens"),
        new Provider("Unavailable OIDC", "/tokens"));
  }

  public record Login(String providerName, Account account, String returnPath) {}

  public record Provider(String displayName, String returnPath) {}

  public record Account(String username, String email, String password) {
    @Override
    public String toString() {
      return "Account[username=%s, email=%s, password=[REDACTED]]".formatted(username, email);
    }
  }
}
