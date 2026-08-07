package io.bookwright.steps;

import com.google.inject.Inject;
import io.bookwright.steps.semaphore.SemaphoreAccessKeySteps;
import io.bookwright.steps.semaphore.SemaphoreAuthSteps;
import io.bookwright.steps.semaphore.SemaphoreInventorySteps;
import io.bookwright.steps.semaphore.SemaphoreProjectSteps;
import io.bookwright.steps.semaphore.SemaphoreRepositorySteps;
import io.bookwright.steps.semaphore.SemaphoreScheduleSteps;
import io.bookwright.steps.semaphore.SemaphoreSystemSteps;
import io.bookwright.steps.semaphore.SemaphoreTaskSteps;
import io.bookwright.steps.semaphore.SemaphoreTemplateSteps;
import io.bookwright.steps.semaphore.SemaphoreUserSteps;
import lombok.Getter;
import lombok.experimental.Accessors;

/** The one object API tests receive. Small on purpose: add a field per domain, not forty-five. */
@Getter
@Accessors(fluent = true)
public class ApiSteps {

  @Inject private AuthApiSteps auth;

  @Inject private BookingApiSteps bookings;

  @Inject private LocalBookingApiSteps localBookings;

  @Inject private UserApiSteps users;

  @Inject private SemaphoreSystemSteps semaphoreSystem;

  @Inject private SemaphoreAuthSteps semaphoreAuth;

  @Inject private SemaphoreProjectSteps semaphoreProjects;

  @Inject private SemaphoreAccessKeySteps semaphoreAccessKeys;

  @Inject private SemaphoreRepositorySteps semaphoreRepositories;

  @Inject private SemaphoreInventorySteps semaphoreInventories;

  @Inject private SemaphoreTemplateSteps semaphoreTemplates;

  @Inject private SemaphoreTaskSteps semaphoreTasks;

  @Inject private SemaphoreScheduleSteps semaphoreSchedules;

  @Inject private SemaphoreUserSteps semaphoreUsers;
}
