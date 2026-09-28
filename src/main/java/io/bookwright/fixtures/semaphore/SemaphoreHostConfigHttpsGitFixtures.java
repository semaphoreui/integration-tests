package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.fixtures.semaphore.SemaphoreHostConfigFixtures.Mapping;
import io.bookwright.util.TestData;

/**
 * A login/password credential mapped onto the private HTTPS Git fixture by URL prefix. The
 * repository itself carries no key, so the mapping is the only source of the Basic Auth.
 */
public record SemaphoreHostConfigHttpsGitFixtures(
    SemaphoreHttpsGitFixtures https,
    ProjectRequest project,
    ProjectRequest unmatchedProject,
    Mapping repositoryUrlMapping,
    Mapping unmatchedUrlMapping) {

  public static SemaphoreHostConfigHttpsGitFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreHostConfigHttpsGitFixtures(
        SemaphoreHttpsGitFixtures.from(data),
        new ProjectRequest("bookwright-host-config-https-" + suffix, false, 0),
        new ProjectRequest("bookwright-host-config-https-unmatched-" + suffix, false, 0),
        new Mapping(SemaphoreHostConfigFixtures.URL_TYPE, "https://git-https-fixture/"),
        new Mapping(SemaphoreHostConfigFixtures.URL_TYPE, "https://git-https-fixture/other/"));
  }
}
