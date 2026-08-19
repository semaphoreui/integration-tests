package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.LoginRequest;

/** Local-only directory identities for the Semaphore LDAPS feature profile. */
public record SemaphoreLdapFixtures(
    Login successfulLogin, Login invalidPassword, Login localEmailConflict) {

  public static SemaphoreLdapFixtures standard() {
    return new SemaphoreLdapFixtures(
        new Login("ldap.user", "ldap.user@bookwright.test", "Bookwright-LDAP-42!"),
        new Login("ldap.invalid", "ldap.invalid@bookwright.test", "Bookwright-LDAP-Wrong-42!"),
        new Login("ldap-admin-conflict", "admin@localhost", "Bookwright-LDAP-Conflict-42!"));
  }

  public record Login(String username, String email, String password) {
    public LoginRequest request() {
      return new LoginRequest(username, password);
    }

    @Override
    public String toString() {
      return "Login[username=%s, email=%s, password=[REDACTED]]".formatted(username, email);
    }
  }
}
