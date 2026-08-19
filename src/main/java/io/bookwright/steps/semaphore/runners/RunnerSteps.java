package io.bookwright.steps.semaphore.runners;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Runner;
import io.bookwright.api.model.testenvironment.DynamicRunnerState;
import io.bookwright.api.semaphore.runners.SemaphoreRunnersApi;
import io.bookwright.api.testenvironment.runners.DynamicRunnerLauncherApi;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import io.qameta.allure.Step;
import java.util.List;

public class RunnerSteps {

  private final SemaphoreRunnersApi api;
  private final DynamicRunnerLauncherApi launcher;

  @Inject
  public RunnerSteps(SemaphoreRunnersApi api, DynamicRunnerLauncherApi launcher) {
    this.api = api;
    this.launcher = launcher;
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

  @Step("Wait for dynamic runner lifecycle of Semaphore task {taskId}")
  public DynamicRunnerState waitUntilDynamicTaskLifecycle(long taskId) {
    return Waits.await("dynamic one-off runner exits after task %d".formatted(taskId))
        .until(this::dynamicRunnerState, state -> hasCompleteLifecycle(state, taskId));
  }

  private DynamicRunnerState dynamicRunnerState() {
    return Calls.body(launcher.getState(), 200, "dynamic runner launcher state");
  }

  private boolean hasCompleteLifecycle(DynamicRunnerState state, long taskId) {
    var eventTypes =
        state.events().stream()
            .filter(event -> event.taskId() == taskId)
            .map(event -> event.type())
            .toList();
    return eventTypes.containsAll(
        List.of("webhook_start", "runner_started", "webhook_finish", "runner_exited"));
  }

  private boolean isOnline(Runner runner) {
    return runner.active()
        && runner.defaultRunner()
        && runner.registered()
        && "online".equals(runner.status());
  }
}
