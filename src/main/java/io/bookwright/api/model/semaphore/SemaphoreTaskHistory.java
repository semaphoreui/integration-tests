package io.bookwright.api.model.semaphore;

import java.util.List;

/** Completed tasks in known newest-first order, owned by one test method. */
public record SemaphoreTaskHistory(List<Task> projectTasks, long templateId) {
  public SemaphoreTaskHistory {
    projectTasks = List.copyOf(projectTasks);
  }

  public List<Task> templateTasks() {
    return projectTasks.stream().filter(task -> task.templateId() == templateId).toList();
  }
}
