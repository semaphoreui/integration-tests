package io.bookwright.tests.external;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.ExternalManagedSetup;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreExternalManagedFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@ExternalManagedSetup
@OwnerDanil
@Feature("External Semaphore managed fixture setup")
class ExternalSemaphoreManagedSetupTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Managed external project fixture exists")
  void managedExternalProjectExists(ApiSteps api, SemaphoreExternalManagedFixtures fixtures) {
    assertThat(
            api.semaphore()
                .backups()
                .restoreProjectIfMissing(fixtures.projectBackup(), fixtures.projectName())
                .name())
        .isEqualTo(fixtures.projectName());
  }
}
