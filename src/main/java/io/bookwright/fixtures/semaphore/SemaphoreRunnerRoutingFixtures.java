package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.Runner;
import io.bookwright.api.model.semaphore.RunnerUpdateRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.util.List;

/** Tagged runner routing, availability and capacity data for the persistent runner profile. */
public record SemaphoreRunnerRoutingFixtures(
    String projectName,
    String matchingTag,
    String missingTag,
    String matchingTemplateName,
    String missingTemplateName,
    String playbook,
    String runningMarker,
    int projectParallelLimit,
    int runnerParallelLimit,
    String waitingStatus,
    String stoppedStatus,
    String errorStatus,
    String unavailableDiagnostic) {

  public static SemaphoreRunnerRoutingFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreRunnerRoutingFixtures(
        "bookwright-runner-routing-" + suffix,
        "bookwright-linux",
        "bookwright-missing",
        "bookwright-tagged-template-" + suffix,
        "bookwright-unmatched-template-" + suffix,
        "long-running.yml",
        "semaphore-bookwright-stop-ready",
        2,
        1,
        "waiting",
        "stopped",
        "error",
        "no runners available");
  }

  public ProjectRequest projectRequest() {
    return new ProjectRequest(projectName, false, projectParallelLimit);
  }

  public RunnerUpdateRequest runnerRequest(Runner runner, boolean active) {
    return new RunnerUpdateRequest(
        runner.name(),
        active,
        runner.defaultRunner(),
        runner.webhook(),
        runnerParallelLimit,
        List.of(matchingTag));
  }

  public TemplateRequest matchingTemplateRequest(
      long projectId, long repositoryId, long inventoryId) {
    return templateRequest(matchingTemplateName, matchingTag, projectId, repositoryId, inventoryId);
  }

  public TemplateRequest missingTemplateRequest(
      long projectId, long repositoryId, long inventoryId) {
    return templateRequest(missingTemplateName, missingTag, projectId, repositoryId, inventoryId);
  }

  private TemplateRequest templateRequest(
      String name, String runnerTag, long projectId, long repositoryId, long inventoryId) {
    return new TemplateRequest(
        name,
        projectId,
        inventoryId,
        repositoryId,
        0,
        playbook,
        "ansible",
        "",
        null,
        false,
        List.of(),
        null,
        runnerTag,
        true);
  }
}
