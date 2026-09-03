package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectUpdateRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.util.List;

/** Project queue limits and a parallel-capable long-running template. */
public record SemaphoreConcurrencyFixtures(
    String projectName,
    String templateName,
    String playbook,
    int serialLimit,
    int parallelLimit,
    String runningMarker,
    String waitingStatus,
    String runningStatus,
    String stoppedStatus) {

  public static SemaphoreConcurrencyFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreConcurrencyFixtures(
        "bookwright-concurrency-" + suffix,
        "bookwright-parallel-template-" + suffix,
        "long-running.yml",
        1,
        2,
        "semaphore-bookwright-stop-ready",
        "waiting",
        "running",
        "stopped");
  }

  public ProjectRequest projectRequest() {
    return new ProjectRequest(projectName, false, serialLimit);
  }

  public ProjectUpdateRequest parallelProjectRequest(long projectId) {
    return new ProjectUpdateRequest(projectId, projectName, false, parallelLimit);
  }

  public TemplateRequest templateRequest(long projectId, long repositoryId, long inventoryId) {
    return new TemplateRequest(
        templateName,
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
        null,
        true);
  }
}
