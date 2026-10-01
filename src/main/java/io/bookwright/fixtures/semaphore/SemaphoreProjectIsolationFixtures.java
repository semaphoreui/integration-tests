package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.util.TestData;

/** Per-test non-admin identity with full permissions in only its own project. */
public record SemaphoreProjectIsolationFixtures(
    ProjectRequest accessibleProject, UserRequest user, String role) {

  public static SemaphoreProjectIsolationFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreProjectIsolationFixtures(
        new ProjectRequest("bookwright-isolation-own-" + suffix, false, 0),
        new UserRequest(
            "Bookwright isolation user",
            "bookwright-isolation-" + suffix,
            "bookwright-isolation-" + suffix + "@localhost",
            "Bookwright-isolation-password-42!",
            false,
            false,
            false),
        "owner");
  }

  public enum ProjectRoute {
    FOREIGN_PROJECT,
    OWN_PROJECT_WITH_FOREIGN_ID;

    public long projectId(long ownProjectId, long foreignProjectId) {
      return this == FOREIGN_PROJECT ? foreignProjectId : ownProjectId;
    }

    public int expectedTaskStatus() {
      // ProjectMiddleware hides foreign projects; GetTaskMiddleware rejects mismatched task IDs.
      return this == FOREIGN_PROJECT ? 404 : 400;
    }
  }
}
