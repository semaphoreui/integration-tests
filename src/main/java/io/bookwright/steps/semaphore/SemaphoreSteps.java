package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.steps.semaphore.accesskeys.AccessKeySteps;
import io.bookwright.steps.semaphore.auth.AuthSteps;
import io.bookwright.steps.semaphore.backups.BackupSteps;
import io.bookwright.steps.semaphore.integrations.IntegrationSteps;
import io.bookwright.steps.semaphore.inventories.InventorySteps;
import io.bookwright.steps.semaphore.projects.ProjectSteps;
import io.bookwright.steps.semaphore.repositories.RepositorySteps;
import io.bookwright.steps.semaphore.runners.RunnerSteps;
import io.bookwright.steps.semaphore.schedules.ScheduleSteps;
import io.bookwright.steps.semaphore.system.SystemSteps;
import io.bookwright.steps.semaphore.tasks.TaskSteps;
import io.bookwright.steps.semaphore.templates.TemplateSteps;
import io.bookwright.steps.semaphore.users.UserSteps;
import io.bookwright.steps.semaphore.variablegroups.VariableGroupSteps;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Compact target facade exposing Semaphore domains. */
@Getter
@Accessors(fluent = true)
public class SemaphoreSteps {

  @Inject private SystemSteps system;
  @Inject private AuthSteps auth;
  @Inject private BackupSteps backups;
  @Inject private ProjectSteps projects;
  @Inject private AccessKeySteps accessKeys;
  @Inject private RepositorySteps repositories;
  @Inject private RunnerSteps runners;
  @Inject private InventorySteps inventories;
  @Inject private IntegrationSteps integrations;
  @Inject private TemplateSteps templates;
  @Inject private TaskSteps tasks;
  @Inject private ScheduleSteps schedules;
  @Inject private UserSteps users;
  @Inject private VariableGroupSteps variableGroups;
}
