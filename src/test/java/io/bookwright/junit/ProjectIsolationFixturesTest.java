package io.bookwright.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreProjectIsolationFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreProjectIsolationFixtures.ProjectRoute;
import io.bookwright.util.TestData;
import org.junit.jupiter.api.Test;

class ProjectIsolationFixturesTest {
  @Test
  void accountAndProjectAreReproducibleAndScopedToTheTestSeed() {
    var data = new TestData(42L, 84L, "isolation");
    var first =
        FixtureCatalog.resolve(SemaphoreProjectIsolationFixtures.class, Configs.main(), data);
    var second = SemaphoreProjectIsolationFixtures.from(new TestData(42L, 85L, "another-test"));

    assertThat(first).isEqualTo(SemaphoreProjectIsolationFixtures.from(data));
    assertThat(first.user().username()).isNotEqualTo(second.user().username());
    assertThat(first.accessibleProject().name()).isNotEqualTo(second.accessibleProject().name());
    assertThat(first.user().admin()).isFalse();
    assertThat(first.user().external()).isFalse();
    assertThat(first.role()).isEqualTo("owner");
    if (first.toString().contains(first.user().password())) {
      throw new AssertionError("Isolation fixture diagnostics expose the user's password");
    }
  }

  @Test
  void routesExerciseBothProjectAuthorizationAndResourceOwnership() {
    assertThat(ProjectRoute.FOREIGN_PROJECT.projectId(7, 11)).isEqualTo(11);
    assertThat(ProjectRoute.OWN_PROJECT_WITH_FOREIGN_ID.projectId(7, 11)).isEqualTo(7);
    assertThat(ProjectRoute.FOREIGN_PROJECT.expectedTaskStatus()).isEqualTo(404);
    assertThat(ProjectRoute.OWN_PROJECT_WITH_FOREIGN_ID.expectedTaskStatus()).isEqualTo(400);
  }
}
