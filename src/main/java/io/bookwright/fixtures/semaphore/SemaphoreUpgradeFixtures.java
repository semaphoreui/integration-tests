package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Inventory;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Repository;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Schedule;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Template;
import io.bookwright.config.MainConfig;

/** Stable data shared by the seed and verify processes of an in-place release upgrade. */
public record SemaphoreUpgradeFixtures(
    ProjectRequest project,
    SecretAccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Template template,
    Schedule schedule,
    String outputMarker) {

  public static SemaphoreUpgradeFixtures from(MainConfig config) {
    return new SemaphoreUpgradeFixtures(
        new ProjectRequest("bookwright-release-upgrade", false, 0),
        new SecretAccessKey(
            "bookwright-upgrade-key",
            "login_password",
            "bookwright-upgrade-user",
            "Bookwright-upgrade-password-42!"),
        new Repository("bookwright-upgrade-repository", config.fixturesRepository(), config.fixturesDefaultBranch()),
        new Inventory(
            "bookwright-upgrade-inventory",
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template("bookwright-upgrade-template", "test-environment/fixtures/ansible/smoke.yml", "ansible", ""),
        new Schedule("bookwright-upgrade-schedule", "0 0 * * *", false, ""),
        "semaphore-bookwright-smoke-ok");
  }
}
