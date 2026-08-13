package io.bookwright.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.assertions.SecretAssertions;
import io.bookwright.config.Configs;
import io.bookwright.fixtures.local.LocalUserFixtures;
import io.bookwright.fixtures.saucedemo.SauceDemoFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
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

    assertThat(sauceDemo.toString()).contains("[REDACTED]");
    assertThat(local.toString()).contains("[REDACTED]");
    assertThat(semaphore.toString()).contains("[REDACTED]");
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
