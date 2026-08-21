package io.bookwright.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.assertions.SecretAssertions;
import io.bookwright.config.Configs;
import io.bookwright.fixtures.local.LocalUserFixtures;
import io.bookwright.fixtures.saucedemo.SauceDemoFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreLdapFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSurveyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreUpgradeFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreVariableGroupFixtures;
import io.bookwright.util.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FixtureArchitectureTest {

  private static final List<String> SCENARIO_LITERALS =
      List.of(
          "locked_out_user",
          "definitely-wrong",
          "incorrect-password",
          "existing.user@bookwright.dev",
          "Sauce Labs Backpack",
          "Sauce Labs Bike Light",
          "Sauce Labs Bolt T-Shirt",
          "Sauce Labs Fleece Jacket",
          "Sauce Labs Onesie",
          "Test.allTheThings() T-Shirt (Red)",
          "\"Products\"",
          "\"Remove\"",
          "Thank you for your order!",
          "Your order has been dispatched",
          ".hasText(\"Bookings\")",
          "Welcome, %s",
          "\"Authentication required\"",
          "Session is missing, invalid, or expired",
          "\"za\"",
          "\"Test\"",
          "\"Guest\"",
          "\"00100\"",
          "\"Wilson\"",
          "\"double\"",
          "\"Tunnel\"",
          "\"Tester\"",
          "999_999_999",
          "bookwright-rbac-guest",
          "Bookwright-test-password-42!",
          "file:///fixtures/ansible",
          "semaphore-bookwright-smoke-ok",
          "forbidden-guest-key",
          "localhost ansible_connection=local",
          "smoke.yml",
          "Bookwright-ssh-passphrase-42!",
          "ldap.user",
          "ldap.user@bookwright.test",
          "Bookwright-LDAP-42!",
          "ldap.invalid",
          "ldap.invalid@bookwright.test",
          "Bookwright-LDAP-Wrong-42!",
          "ldap-admin-conflict",
          "Bookwright-LDAP-Conflict-42!",
          "ssh://fixture@ssh-fixture:22/repositories/ansible",
          "semaphore-bookwright-ssh-target-ok",
          "Bookwright Dex",
          "oidc.user@bookwright.test",
          "Bookwright-OIDC-42!",
          "Bookwright-OIDC-Conflict-42!",
          "Unavailable OIDC",
          "0 0 * * *");

  @Test
  void stepsAndProductTestsDoNotOwnScenarioFixtures() throws Exception {
    List<String> violations = new ArrayList<>();
    inspect(Path.of("src/main/java/io/bookwright/steps"), violations);
    inspect(Path.of("src/test/java/io/bookwright/tests/api"), violations);
    inspect(Path.of("src/test/java/io/bookwright/tests/ui"), violations);
    inspect(Path.of("src/test/java/io/bookwright/tests/integration"), violations);
    inspect(Path.of("src/test/java/io/bookwright/tests/db"), violations);
    inspect(Path.of("src/test/java/io/bookwright/tests/semaphore"), violations);

    assertThat(violations).as("scenario literals outside typed fixtures/TestData").isEmpty();
  }

  @Test
  void fixtureDiagnosticsRedactPasswords() {
    SauceDemoFixtures sauceDemo = SauceDemoFixtures.from(Configs.main());
    LocalUserFixtures local = LocalUserFixtures.from(Configs.main());
    SemaphoreFixtures semaphore =
        SemaphoreFixtures.from(Configs.main(), new TestData(1L, 2L, "fixture-redaction"));
    SemaphoreLdapFixtures ldap = SemaphoreLdapFixtures.standard();
    SemaphoreOidcFixtures oidc = SemaphoreOidcFixtures.standard();
    SemaphoreSshFixtures.SshAccessKey sshKey =
        new SemaphoreSshFixtures.SshAccessKey(
            "fixture-key", "ssh", "fixture", "ssh-passphrase-secret", "ssh-private-key-secret");
    SemaphoreUpgradeFixtures upgrade = SemaphoreUpgradeFixtures.standard();
    SemaphoreVariableGroupFixtures variableGroup =
        SemaphoreVariableGroupFixtures.from(new TestData(1L, 2L, "fixture-redaction"));
    SemaphoreSurveyFixtures survey =
        SemaphoreSurveyFixtures.from(new TestData(1L, 2L, "fixture-redaction"));

    SecretAssertions.absent(
        "SauceDemo fixture diagnostics", sauceDemo.toString(), sauceDemo.standardUser().password());
    SecretAssertions.absent(
        "SauceDemo fixture diagnostics",
        sauceDemo.toString(),
        sauceDemo.invalidPassword().user().password());
    SecretAssertions.absent(
        "local fixture diagnostics", local.toString(), local.invalidExistingUser().password());
    SecretAssertions.absent(
        "Semaphore fixture diagnostics", semaphore.toString(), semaphore.rbac().password());
    SecretAssertions.absent(
        "Semaphore fixture diagnostics", semaphore.toString(), semaphore.invalidLogin().password());
    SecretAssertions.absent(
        "Semaphore LDAP fixture diagnostics", ldap.toString(), ldap.successfulLogin().password());
    SecretAssertions.absent(
        "Semaphore LDAP fixture diagnostics", ldap.toString(), ldap.invalidPassword().password());
    SecretAssertions.absent(
        "Semaphore LDAP fixture diagnostics",
        ldap.toString(),
        ldap.localEmailConflict().password());
    SecretAssertions.absent(
        "Semaphore OIDC fixture diagnostics",
        oidc.toString(),
        oidc.successfulLogin().account().password());
    SecretAssertions.absent(
        "Semaphore OIDC fixture diagnostics",
        oidc.toString(),
        oidc.localEmailConflict().account().password());
    SecretAssertions.absent(
        "Semaphore upgrade fixture diagnostics",
        upgrade.toString(),
        upgrade.accessKey().password());
    SecretAssertions.absent("Semaphore SSH key diagnostics", sshKey.toString(), sshKey);
    SecretAssertions.absent(
        "Semaphore SSH request diagnostics", sshKey.request(1).toString(), sshKey);
    SecretAssertions.absent(
        "Semaphore SSH rotation diagnostics", sshKey.rotationRequest(1, 2).toString(), sshKey);
    SecretAssertions.absent(
        "Semaphore Variable Group fixture diagnostics",
        variableGroup.toString(),
        variableGroup.variableSecret().value());
    SecretAssertions.absent(
        "Semaphore Variable Group request diagnostics",
        variableGroup.createRequest(1).toString(),
        variableGroup.environmentSecret().value());
    SecretAssertions.absent(
        "Semaphore survey fixture diagnostics", survey.toString(), survey.taskSecret().value());
    SecretAssertions.absent(
        "Semaphore survey task request diagnostics",
        survey.taskRequest(1).toString(),
        survey.taskSecret().value());

    assertThat(sauceDemo.toString()).contains("[REDACTED]");
    assertThat(local.toString()).contains("[REDACTED]");
    assertThat(semaphore.toString()).contains("[REDACTED]");
    assertThat(ldap.toString()).contains("[REDACTED]");
    assertThat(oidc.toString()).contains("[REDACTED]");
    assertThat(upgrade.toString()).contains("[REDACTED]");
    assertThat(sshKey.toString()).contains("[REDACTED]");
    assertThat(variableGroup.toString()).contains("[REDACTED]");
    assertThat(survey.toString()).contains("[REDACTED]");
  }

  private void inspect(Path root, List<String> violations) throws IOException {
    if (Files.notExists(root)) {
      return;
    }
    try (Stream<Path> files = Files.walk(root)) {
      for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String content = Files.readString(source);
        SCENARIO_LITERALS.stream()
            .filter(content::contains)
            .map(literal -> source + " contains " + literal)
            .forEach(violations::add);
      }
    }
  }
}
