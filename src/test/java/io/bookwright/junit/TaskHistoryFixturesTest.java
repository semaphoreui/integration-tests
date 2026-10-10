package io.bookwright.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreTaskHistoryFixtures;
import io.bookwright.util.TestData;
import org.junit.jupiter.api.Test;

class TaskHistoryFixturesTest {
  @Test
  void fixtureIsRegisteredAndNamesAreScopedToTheTestSeed() {
    var data = new TestData(42L, 84L, "history");
    var first = FixtureCatalog.resolve(SemaphoreTaskHistoryFixtures.class, Configs.main(), data);
    assertThat(first).isEqualTo(SemaphoreTaskHistoryFixtures.from(data));
    assertThat(first.siblingTemplate().name())
        .isNotEqualTo(
            SemaphoreTaskHistoryFixtures.from(new TestData(42L, 85L, "another-history"))
                .siblingTemplate()
                .name());
  }

  @Test
  void addingCursorDoesNotMutateTheReusableFirstPageQuery() {
    var first = SemaphoreTaskHistoryFixtures.from(new TestData(42L, 84L, "history")).page();
    var next = first.before(17);
    assertThat(first.before()).isNull();
    assertThat(next.before()).isEqualTo(17);
    assertThat(next.count()).isEqualTo(first.count());
    assertThat(next.limit()).isEqualTo(first.limit());
  }
}
