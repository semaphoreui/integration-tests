package io.bookwright.steps.semaphore.runners;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Runner;
import io.bookwright.api.model.semaphore.RunnerTag;
import io.bookwright.api.model.semaphore.RunnerUpdateRequest;
import io.bookwright.api.model.testenvironment.DynamicRunnerState;
import io.bookwright.api.semaphore.runners.SemaphoreRunnersApi;
import io.bookwright.api.testenvironment.runners.DynamicRunnerLauncherApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import io.qameta.allure.Step;
import java.util.List;

public class RunnerSteps {

  private final SemaphoreRunnersApi api;
  private final DynamicRunnerLauncherApi launcher;
  private final TeardownStorage teardown;

  @Inject
  public RunnerSteps(
      SemaphoreRunnersApi api, DynamicRunnerLauncherApi launcher, TeardownStorage teardown) {
    this.api = api;
    this.launcher = launcher;
    this.teardown = teardown;
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

  @Step("Get global Semaphore runner {runnerId}")
  public Runner getRunner(long runnerId) {
    return Calls.body(api.getRunner(runnerId), 200, "global runner");
  }

  @Step("Temporarily configure global Semaphore runner {runnerId}")
  public Runner configureTemporarily(long runnerId, RunnerUpdateRequest request) {
    Runner original = getRunner(runnerId);
    teardown.push(
        "Restore global Semaphore runner " + runnerId,
        () ->
            Calls.expectStatus(
                api.updateRunner(runnerId, RunnerUpdateRequest.from(original)), 204));
    return updateRunner(runnerId, request);
  }

  @Step("Update global Semaphore runner {runnerId}")
  public Runner updateRunner(long runnerId, RunnerUpdateRequest request) {
    Calls.expectStatus(api.updateRunner(runnerId, request), 204);
    return getRunner(runnerId);
  }

  @Step("Wait for global Semaphore runner {runnerId} to become active and online")
  public Runner waitUntilRunnerIsOnline(long runnerId) {
    return Waits.await("global Semaphore runner %d is active and online".formatted(runnerId))
        .until(
            () -> getRunner(runnerId),
            runner -> runner.active() && "online".equals(runner.status()));
  }

  @Step("Wait for global Semaphore runner tag {tag}")
  public RunnerTag waitUntilTagIsAvailable(String tag) {
    List<RunnerTag> tags =
        Waits.await("global Semaphore runner tag '%s' is available".formatted(tag))
            .until(
                () -> Calls.body(api.getRunnerTags(), 200, "global runner tags"),
                items -> items.stream().anyMatch(item -> tag.equals(item.tag())));
    return tags.stream()
        .filter(item -> tag.equals(item.tag()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Semaphore reported runner tag '%s' while waiting but returned none"
                        .formatted(tag)));
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
