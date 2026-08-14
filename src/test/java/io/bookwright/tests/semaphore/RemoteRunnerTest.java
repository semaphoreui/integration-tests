package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore remote runners")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = ".*runner.*")
class RemoteRunnerTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Persistent remote runner registers and becomes online")
  void persistentRemoteRunnerIsAvailable(ApiSteps api) {
    var runner = api.semaphore().runners().waitUntilDefaultRunnerIsOnline();

    assertThat(runner.id()).isPositive();
    assertThat(runner.name()).isNotBlank();
    assertThat(runner.active()).isTrue();
    assertThat(runner.defaultRunner()).isTrue();
    assertThat(runner.registered()).isTrue();
    assertThat(runner.status()).isEqualTo("online");
    assertThat(runner.touched()).isNotNull();
    assertThat(runner.maxParallelTasks()).isPositive();
  }
}
