package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Inventory;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Repository;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.Template;

/** Stable data shared by the phases of a database-encryption key rotation. */
public record SemaphoreEncryptionRotationFixtures(
    ProjectRequest project,
    SecretAccessKey oldPrimaryKey,
    SecretAccessKey newPrimaryKey,
    SecretAccessKey postRekeyKey,
    Repository repository,
    Inventory inventory,
    Template template,
    String outputMarker) {

  public static SemaphoreEncryptionRotationFixtures from(MainConfig config) {
    return new SemaphoreEncryptionRotationFixtures(
        new ProjectRequest("bookwright-encryption-rotation", false, 0),
        new SecretAccessKey(
            "bookwright-old-primary-key",
            "login_password",
            "bookwright-old-primary-user",
            "Bookwright-old-primary-password-42!"),
        new SecretAccessKey(
            "bookwright-new-primary-key",
            "login_password",
            "bookwright-new-primary-user",
            "Bookwright-new-primary-password-42!"),
        new SecretAccessKey(
            "bookwright-post-rekey-key",
            "login_password",
            "bookwright-post-rekey-user",
            "Bookwright-post-rekey-password-42!"),
        new Repository(
            "bookwright-encryption-repository",
            config.fixturesRepository(),
            config.fixturesDefaultBranch()),
        new Inventory(
            "bookwright-encryption-inventory",
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template(
            "bookwright-encryption-template",
            "test-environment/fixtures/ansible/smoke.yml",
            "ansible",
            ""),
        "semaphore-bookwright-smoke-ok");
  }
}
