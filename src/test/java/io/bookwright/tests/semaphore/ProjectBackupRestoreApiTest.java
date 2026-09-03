package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreBackupFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore project backup and restore")
class ProjectBackupRestoreApiTest {

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Project backup restores executable resources without task history or secrets")
  void projectBackupRestoresResourcesWithoutHistoryOrSecrets(
      ApiSteps api, TestStore store, SemaphoreFixtures core, SemaphoreBackupFixtures fixture) {
    var source = api.semaphore().projects().createProject(core.projects().secrets());
    var key =
        api.semaphore().accessKeys().create(source.id(), core.accessKey().request(source.id()));
    api.semaphore().accessKeys().createAndVerifyMasked(source.id(), core.secretAccessKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(source.id(), core.repositories().primary().request(source.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(source.id(), core.inventory().request(source.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                source.id(),
                core.templates().primary().request(source.id(), repository.id(), inventory.id()));
    var sourceTask = api.semaphore().tasks().startAndWait(source.id(), template.id());
    api.semaphore()
        .schedules()
        .create(source.id(), core.schedule().request(source.id(), template.id()));

    var backup =
        api.semaphore().backups().exportProjectAndVerifyMasked(source.id(), core.secretAccessKey());
    api.semaphore()
        .backups()
        .verifyCannotRestore(
            api.semaphore().auth().loginAs(store.semaphoreRbacUser()),
            backup,
            fixture.unauthorizedProjectName());
    api.semaphore()
        .backups()
        .verifyMissingTemplateRepositoryRejected(
            backup, fixture.missingLinkProjectName(), fixture.missingRepositoryName());
    var duplicateRestore =
        api.semaphore()
            .backups()
            .restoreWithDuplicateRepositoriesCurrentlyAccepted(
                backup, fixture.duplicateProjectName());
    var restored = api.semaphore().backups().restoreProject(backup, fixture.restoredProjectName());

    assertThat(api.semaphore().repositories().getRepositories(duplicateRestore.id()))
        .filteredOn(item -> item.name().equals(core.repositories().primary().name()))
        .hasSize(2);
    assertThat(api.semaphore().tasks().getTasks(restored.id())).isEmpty();

    var restoredKey =
        api.semaphore().accessKeys().requireByName(restored.id(), core.accessKey().name());
    var restoredRepository =
        api.semaphore()
            .repositories()
            .requireByName(restored.id(), core.repositories().primary().name());
    var restoredInventory =
        api.semaphore().inventories().requireByName(restored.id(), core.inventory().name());
    var restoredTemplate =
        api.semaphore().templates().requireByName(restored.id(), core.templates().primary().name());
    var restoredSchedule =
        api.semaphore().schedules().requireByName(restored.id(), core.schedule().name());
    var restoredTask = api.semaphore().tasks().startAndWait(restored.id(), restoredTemplate.id());

    assertThat(restored.name()).isEqualTo(fixture.restoredProjectName());
    assertThat(restored.id()).isNotEqualTo(source.id());
    assertThat(restoredKey.id()).isNotEqualTo(key.id());
    assertThat(restoredRepository.sshKeyId()).isEqualTo(restoredKey.id());
    assertThat(restoredInventory.sshKeyId()).isEqualTo(restoredKey.id());
    assertThat(restoredTemplate.repositoryId()).isEqualTo(restoredRepository.id());
    assertThat(restoredTemplate.inventoryId()).isEqualTo(restoredInventory.id());
    assertThat(restoredSchedule.templateId()).isEqualTo(restoredTemplate.id());
    assertThat(restoredTask.id()).isNotEqualTo(sourceTask.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            restored.id(), restoredTask.id(), core.expectations().outputMarker());
  }
}
