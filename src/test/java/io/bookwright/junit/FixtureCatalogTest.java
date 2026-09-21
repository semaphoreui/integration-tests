package io.bookwright.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreAuthLifecycleFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreBackupFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreUpgradeFixtures;
import io.bookwright.util.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterResolutionException;

class FixtureCatalogTest {

  private static final TestData TEST_DATA = new TestData(42L, 84L, "fixture-catalog");

  @Test
  void recognizesOnlyRegisteredFixtureTypes() {
    assertThat(FixtureCatalog.supports(SemaphoreBackupFixtures.class)).isTrue();
    assertThat(FixtureCatalog.supports(SemaphoreAuthLifecycleFixtures.class)).isTrue();
    assertThat(FixtureCatalog.supports(SemaphoreTotpFixtures.class)).isTrue();
    assertThat(FixtureCatalog.supports(String.class)).isFalse();
  }

  @Test
  void createsFixturesForEverySupportedFactoryShape() {
    var config = Configs.main();

    assertThat(FixtureCatalog.resolve(SemaphoreTotpFixtures.class, config, TEST_DATA))
        .isExactlyInstanceOf(SemaphoreTotpFixtures.class);
    assertThat(FixtureCatalog.resolve(SemaphoreUpgradeFixtures.class, config, TEST_DATA))
        .isExactlyInstanceOf(SemaphoreUpgradeFixtures.class);
    assertThat(FixtureCatalog.resolve(SemaphoreBackupFixtures.class, config, TEST_DATA))
        .isExactlyInstanceOf(SemaphoreBackupFixtures.class);
    assertThat(FixtureCatalog.resolve(SemaphoreFixtures.class, config, TEST_DATA))
        .isExactlyInstanceOf(SemaphoreFixtures.class);
  }

  @Test
  void reportsAnActionableErrorForUnknownFixtureType() {
    assertThatThrownBy(() -> FixtureCatalog.resolve(String.class, Configs.main(), TEST_DATA))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining(String.class.getName());
  }
}
