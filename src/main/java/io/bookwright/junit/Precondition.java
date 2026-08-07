package io.bookwright.junit;

import io.bookwright.api.model.CreatedBooking;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.config.Configs;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.steps.ApiSteps;
import io.bookwright.util.TestData;
import java.util.function.BiConsumer;

/** Catalog of product-state preconditions shared through typed {@link TestStore} accessors. */
public enum Precondition implements IPrecondition {
  BOOKING_EXISTS(
      "Create a booking",
      (api, store) -> {
        TestData data = store.testData();
        CreatedBooking created =
            api.restfulBooker()
                .bookings()
                .create(data.booking(), api.restfulBooker().auth().session());
        store.putBooking(created);
      }),

  SEMAPHORE_ADMIN_SESSION(
      "Login to Semaphore as administrator", (api, store) -> api.semaphore().auth().login()),

  SEMAPHORE_PROJECT_EXISTS(
      "Create an isolated Semaphore project",
      (api, store) -> {
        SemaphoreFixtures fixtures = SemaphoreFixtures.from(Configs.main(), store.testData());
        Project project = api.semaphore().projects().createProject(fixtures.projects().secrets());
        store.putSemaphoreProject(project);
      }),

  SEMAPHORE_RBAC_USER_EXISTS(
      "Ensure the Semaphore RBAC fixture user exists",
      (api, store) -> {
        SemaphoreFixtures.Rbac rbac =
            SemaphoreFixtures.from(Configs.main(), store.testData()).rbac();
        User user =
            api.semaphore().users().getUsers().stream()
                .filter(candidate -> rbac.username().equals(candidate.username()))
                .findFirst()
                .orElseGet(() -> api.semaphore().users().create(rbac.userRequest()));
        store.putSemaphoreRbacUser(rbac.account(user));
      });

  static final String BOOKING_KEY = "createdBooking";
  static final String SEMAPHORE_PROJECT_KEY = "semaphoreProject";
  static final String SEMAPHORE_RBAC_USER_KEY = "semaphoreRbacUser";

  private final String title;
  private final BiConsumer<ApiSteps, TestStore> action;

  Precondition(String title, BiConsumer<ApiSteps, TestStore> action) {
    this.title = title;
    this.action = action;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public void execute(ApiSteps api, TestStore store) {
    action.accept(api, store);
  }
}
