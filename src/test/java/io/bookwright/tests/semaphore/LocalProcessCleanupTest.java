package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.fixtures.semaphore.SemaphoreProcessCleanupFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore local process cleanup")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local")
class LocalProcessCleanupTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Normal task completion cleans up a background descendant")
  void normalCompletionCleansUpBackgroundDescendant(
      ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var templates = createTemplates(api, fixtures);

    long startedAt = System.nanoTime();
    var completedTask =
        api.semaphore().tasks().startAndWait(templates.main().projectId(), templates.main().id());
    var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
    var mainOutput =
        api.semaphore().tasks().getTaskOutputText(templates.main().projectId(), completedTask.id());

    var verificationTask =
        api.semaphore()
            .tasks()
            .startAndWait(
                templates.verifier().projectId(),
                fixtures.verificationRequest(templates.verifier().id(), completedTask.id()));
    var verificationOutput =
        api.semaphore()
            .tasks()
            .getTaskOutputText(templates.verifier().projectId(), verificationTask.id());

    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(elapsed).isLessThan(fixtures.expectations().maximumCompletionTime());
    assertThat(mainOutput)
        .contains(fixtures.expectations().stdoutMarker())
        .contains(fixtures.expectations().stderrMarker());
    assertThat(verificationOutput).contains(fixtures.expectations().childGoneMarker());
  }

  private CreatedTemplates createTemplates(ApiSteps api, SemaphoreProcessCleanupFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var main =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.templates().main().request(project.id(), repository.id(), inventory.id()));
    var verifier =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .verifier()
                    .request(project.id(), repository.id(), inventory.id()));
    return new CreatedTemplates(main, verifier);
  }

  private record CreatedTemplates(Template main, Template verifier) {}
}
