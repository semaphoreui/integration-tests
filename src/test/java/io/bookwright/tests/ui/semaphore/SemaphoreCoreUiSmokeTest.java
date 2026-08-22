package io.bookwright.tests.ui.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.SensitiveUi;
import io.bookwright.annotations.Smoke;
import io.bookwright.annotations.Ui;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.UiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Ui
@Smoke
@SensitiveUi
@OwnerDanil
@Feature("Semaphore core browser smoke")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "core-sqlite-local")
class SemaphoreCoreUiSmokeTest {

  @Test
  @DisplayName("Administrator signs in through the password form")
  void administratorSignsIn(UiSteps ui) {
    ui.semaphore().core().login();
  }

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
  })
  @DisplayName("Administrator starts an executable template through the browser")
  void administratorRunsTemplate(UiSteps ui, ApiSteps api, TestStore store) {
    ui.semaphore().core().login();
    var started =
        ui.semaphore()
            .core()
            .runTemplate(store.semaphoreProject().id(), store.semaphoreTemplate().id());

    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskSucceeds(store.semaphoreProject().id(), started.id())
                .status())
        .isEqualTo("success");
  }

  @Test
  @DisplayName("Project name is validated in the browser before submission")
  void emptyProjectNameIsRejectedBeforeSubmission(UiSteps ui) {
    ui.semaphore().core().login();
    ui.semaphore().core().emptyProjectNameIsRejected();
  }
}
