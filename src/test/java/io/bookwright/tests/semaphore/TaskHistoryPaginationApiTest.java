package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskHistoryPage;
import io.bookwright.api.model.semaphore.TaskHistoryQuery;
import io.bookwright.fixtures.semaphore.SemaphoreTaskHistoryFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTaskHistoryFixtures.Scope;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Api
@OwnerDanil
@Feature("Semaphore task history pagination")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS,
  Precondition.SEMAPHORE_TASK_HISTORY_EXISTS
})
class TaskHistoryPaginationApiTest {
  @ParameterizedTest(name = "{0}")
  @EnumSource(Scope.class)
  @DisplayName("History pages contain every matching task exactly once in newest-first order")
  void pagesPreserveOrderAndScope(
      Scope scope, ApiSteps api, TestStore store, SemaphoreTaskHistoryFixtures fixtures) {
    var expected = expectedIds(scope, store);
    var first = page(scope, api, store, fixtures.page());
    assertThat(first.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(expected.subList(0, fixtures.page().count()));
    assertThat(first.hasNext()).isTrue();

    var last = page(scope, api, store, fixtures.page().before(first.tasks().getLast().id()));
    assertThat(last.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(expected.subList(fixtures.page().count(), expected.size()));
    assertThat(last.hasNext()).isFalse();

    var exhausted = page(scope, api, store, fixtures.page().before(last.tasks().getLast().id()));
    assertThat(exhausted.tasks()).isEmpty();
    assertThat(exhausted.hasNext()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Scope.class)
  @DisplayName("A task added after the first page does not shift or duplicate the next page")
  void cursorSurvivesNewTask(
      Scope scope, ApiSteps api, TestStore store, SemaphoreTaskHistoryFixtures fixtures) {
    var first = page(scope, api, store, fixtures.page());
    assertThat(first.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(expectedIds(scope, store).subList(0, fixtures.page().count()));
    var added =
        api.semaphore()
            .tasks()
            .startAndWait(store.semaphoreProject().id(), store.semaphoreTemplate().id());

    assertThat(page(scope, api, store, fixtures.page()).tasks().getFirst().id())
        .isEqualTo(added.id());
    var next = page(scope, api, store, fixtures.page().before(first.tasks().getLast().id()));
    var expected = expectedIds(scope, store);
    assertThat(next.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(expected.subList(fixtures.page().count(), expected.size()));
    assertThat(next.hasNext()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Scope.class)
  @DisplayName("Legacy limit is honored, while count takes precedence when both are supplied")
  void countTakesPrecedenceOverLegacyLimit(
      Scope scope, ApiSteps api, TestStore store, SemaphoreTaskHistoryFixtures fixtures) {
    var legacy = page(scope, api, store, fixtures.legacyPage());
    assertThat(legacy.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(
            expectedIds(scope, store).subList(0, fixtures.legacyPage().limit()));
    assertThat(legacy.hasNext()).isTrue();

    var preferred = page(scope, api, store, fixtures.countBeforeLimit());
    assertThat(preferred.tasks())
        .extracting(Task::id)
        .containsExactlyElementsOf(
            expectedIds(scope, store).subList(0, fixtures.countBeforeLimit().count()));
    assertThat(preferred.hasNext()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Scope.class)
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
  })
  @DisplayName("An empty project or template has an empty history and no next page")
  void emptyHistoryHasNoNextPage(
      Scope scope, ApiSteps api, TestStore store, SemaphoreTaskHistoryFixtures fixtures) {
    var empty = page(scope, api, store, fixtures.page());
    assertThat(empty.tasks()).isEmpty();
    assertThat(empty.hasNext()).isFalse();
  }

  private TaskHistoryPage page(Scope scope, ApiSteps api, TestStore store, TaskHistoryQuery query) {
    return scope == Scope.PROJECT
        ? api.semaphore().taskHistory().projectPage(store.semaphoreProject().id(), query)
        : api.semaphore()
            .taskHistory()
            .templatePage(store.semaphoreProject().id(), store.semaphoreTemplate().id(), query);
  }

  private List<Long> expectedIds(Scope scope, TestStore store) {
    var history = store.semaphoreTaskHistory();
    return (scope == Scope.PROJECT ? history.projectTasks() : history.templateTasks())
        .stream().map(Task::id).toList();
  }
}
