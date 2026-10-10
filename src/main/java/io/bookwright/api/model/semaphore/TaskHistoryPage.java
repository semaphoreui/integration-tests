package io.bookwright.api.model.semaphore;

import java.util.List;

public record TaskHistoryPage(List<Task> tasks, boolean hasNext) {
  public TaskHistoryPage {
    tasks = List.copyOf(tasks);
  }
}
