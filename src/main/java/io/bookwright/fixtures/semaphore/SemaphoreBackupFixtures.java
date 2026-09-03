package io.bookwright.fixtures.semaphore;

import io.bookwright.util.TestData;

/** Names and expectations for a project backup/restore round trip. */
public record SemaphoreBackupFixtures(
    String restoredProjectName,
    String unauthorizedProjectName,
    String missingLinkProjectName,
    String duplicateProjectName,
    String missingRepositoryName) {

  public static SemaphoreBackupFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreBackupFixtures(
        "bookwright-restored-" + suffix,
        "bookwright-unauthorized-restore-" + suffix,
        "bookwright-missing-link-" + suffix,
        "bookwright-duplicate-" + suffix,
        "bookwright-missing-repository-" + suffix);
  }
}
