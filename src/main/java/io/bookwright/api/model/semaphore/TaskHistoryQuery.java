package io.bookwright.api.model.semaphore;

/** Null query fields are omitted by Retrofit. */
public record TaskHistoryQuery(Integer count, Integer limit, Long before) {
  public TaskHistoryQuery before(long taskId) {
    return new TaskHistoryQuery(count, limit, taskId);
  }
}
