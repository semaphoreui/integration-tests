package io.bookwright.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.util.TestData;
import org.junit.jupiter.api.Test;

class TokenOwnerFixturesTest {
  @Test
  void ownersAreDistinctNonAdminAccountsScopedToTheTestSeed() {
    var data = new TestData(42L, 84L, "token-owners");
    var fixture = FixtureCatalog.resolve(SemaphoreTokenFixtures.class, Configs.main(), data);
    var next = SemaphoreTokenFixtures.from(new TestData(42L, 85L, "next-test"));

    assertThat(fixture).isEqualTo(SemaphoreTokenFixtures.from(data));
    assertThat(fixture.ownerRequest().username())
        .isNotEqualTo(fixture.otherOwnerRequest().username());
    assertThat(fixture.ownerRequest().email()).isNotEqualTo(fixture.otherOwnerRequest().email());
    assertThat(fixture.ownerRequest().username()).isNotEqualTo(next.ownerRequest().username());
    assertThat(fixture.otherOwnerRequest().username())
        .isNotEqualTo(next.otherOwnerRequest().username());
    for (var account : java.util.List.of(fixture.ownerRequest(), fixture.otherOwnerRequest())) {
      assertThat(account.admin()).isFalse();
      assertThat(account.external()).isFalse();
      assertThat(account.toString().contains(account.password())).isFalse();
    }
  }
}
