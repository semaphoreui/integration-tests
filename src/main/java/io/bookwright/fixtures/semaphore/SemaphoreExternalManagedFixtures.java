package io.bookwright.fixtures.semaphore;

import io.bookwright.config.ExternalManagedConfig;

/** References to task fixtures that are deliberately pre-created on a user-managed stand. */
public record SemaphoreExternalManagedFixtures(
    long projectId, Template templateA, Template templateB) {

  public static SemaphoreExternalManagedFixtures from(ExternalManagedConfig config) {
    if (!config.mutationsAllowed()) {
      throw new IllegalStateException(
          "Managed external checks are disabled. Set EXTERNAL_MUTATIONS_ALLOWED=true explicitly.");
    }

    var fixtures =
        new SemaphoreExternalManagedFixtures(
            config.projectId(),
            new Template(config.templateAId(), config.markerA()),
            new Template(config.templateBId(), config.markerB()));
    fixtures.validate();
    return fixtures;
  }

  private void validate() {
    if (projectId <= 0) {
      throw new IllegalStateException(
          "EXTERNAL_MANAGED_PROJECT_ID must be a positive Semaphore project ID");
    }
    if (templateA.id() == templateB.id()) {
      throw new IllegalStateException(
          "EXTERNAL_MANAGED_TEMPLATE_A_ID and EXTERNAL_MANAGED_TEMPLATE_B_ID must be different");
    }
    if (templateA.marker().equals(templateB.marker())) {
      throw new IllegalStateException(
          "EXTERNAL_MANAGED_MARKER_A and EXTERNAL_MANAGED_MARKER_B must be different");
    }
  }

  public record Template(long id, String marker) {
    public Template {
      if (id <= 0) {
        throw new IllegalStateException("External managed template IDs must be positive");
      }
      if (marker == null || marker.isBlank()) {
        throw new IllegalStateException("External managed output markers must not be blank");
      }
    }
  }
}
