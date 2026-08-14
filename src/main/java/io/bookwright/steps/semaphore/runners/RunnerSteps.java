package io.bookwright.steps.semaphore.runners;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Runner;
import io.bookwright.api.semaphore.runners.SemaphoreRunnersApi;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import io.qameta.allure.Step;
import java.util.List;

public class RunnerSteps {

  private final SemaphoreRunnersApi api;

  @Inject
  public RunnerSteps(SemaphoreRunnersApi api) {
    this.api = api;
  }

  @Step("Get global Semaphore runners")
  public List<Runner> getRunners() {
    return Calls.body(api.getRunners(), 200, "global runners");
  }

  @Step("Wait for the active default Semaphore runner")
  public Runner waitUntilDefaultRunnerIsOnline() {
    List<Runner> runners =
        Waits.awaitSlow("the active default Semaphore runner is online")
            .until(this::getRunners, items -> items.stream().anyMatch(this::isOnline));
    return runners.stream()
        .filter(this::isOnline)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Semaphore reported a matching runner while waiting, but returned none"));
  }

  private boolean isOnline(Runner runner) {
    return runner.active()
        && runner.defaultRunner()
        && runner.registered()
        && "online".equals(runner.status());
  }
}
