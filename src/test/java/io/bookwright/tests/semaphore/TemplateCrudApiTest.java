package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreTemplateCrudFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Template CRUD")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
})
class TemplateCrudApiTest {

  @Test
  @DisplayName("Template can be read, updated and deleted")
  void templateLifecycle(ApiSteps api, TestStore store, SemaphoreTemplateCrudFixtures fixture) {
    var template = store.semaphoreTemplate();
    assertThat(api.semaphore().templates().get(template.projectId(), template.id()))
        .isEqualTo(template);

    assertThat(
            api.semaphore()
                .templates()
                .update(template.projectId(), template.id(), fixture.updateRequest(template)))
        .satisfies(
            saved -> {
              assertThat(saved.id()).isEqualTo(template.id());
              assertThat(saved.projectId()).isEqualTo(template.projectId());
              assertThat(saved.name()).isEqualTo(fixture.updatedName());
              assertThat(saved.playbook()).isEqualTo(fixture.playbook());
              assertThat(saved.repositoryId()).isEqualTo(template.repositoryId());
              assertThat(saved.inventoryId()).isEqualTo(template.inventoryId());
              assertThat(saved.arguments()).isEqualTo(fixture.arguments());
              assertThat(saved.allowOverrideArgsInTask()).isTrue();
            });

    api.semaphore().templates().delete(template.projectId(), template.id());
    api.semaphore().templates().verifyAbsent(template.projectId(), template.id());
  }

  @Test
  @DisplayName("Task executes the updated playbook with the saved check-mode argument")
  void updatedTemplateRemainsExecutable(
      ApiSteps api, TestStore store, SemaphoreTemplateCrudFixtures fixture) {
    var template = store.semaphoreTemplate();
    api.semaphore()
        .templates()
        .update(template.projectId(), template.id(), fixture.updateRequest(template));

    var task = api.semaphore().tasks().startAndWait(template.projectId(), template.id());

    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(template.projectId(), task.id(), fixture.checkModeMarker());
  }
}
