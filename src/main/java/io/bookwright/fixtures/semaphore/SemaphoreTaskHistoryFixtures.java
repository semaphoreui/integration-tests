package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.TaskHistoryQuery;
import io.bookwright.util.TestData;

public record SemaphoreTaskHistoryFixtures(
    SemaphoreFixtures.Template siblingTemplate,
    TaskHistoryQuery page,
    TaskHistoryQuery legacyPage,
    TaskHistoryQuery countBeforeLimit) {
  public static SemaphoreTaskHistoryFixtures from(TestData data) {
    return new SemaphoreTaskHistoryFixtures(
        new SemaphoreFixtures.Template(
            "bookwright-history-sibling-" + Long.toUnsignedString(data.testSeed(), 36),
            "ansible/smoke.yml",
            "ansible",
            ""),
        new TaskHistoryQuery(2, null, null),
        new TaskHistoryQuery(null, 1, null),
        new TaskHistoryQuery(2, 1, null));
  }

  public enum Scope {
    PROJECT,
    TEMPLATE
  }
}
