package io.bookwright.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiCoverageReporterTest {

  @Test
  void writesMachineReadableReportFromExistingObservations(@TempDir Path temp) throws Exception {
    Path specification = temp.resolve("api-docs.yml");
    Path observations = temp.resolve("observations.tsv");
    Path report = temp.resolve("report.json");
    Files.writeString(
        specification,
        """
        swagger: '2.0'
        basePath: /api
        paths:
          /projects:
            get:
              tags: [project]
        """);
    Files.writeString(observations, "200\tGET\t/api/projects%n".formatted());

    System.setProperty("api.coverage.spec", specification.toString());
    System.setProperty("api.coverage.observations", observations.toString());
    System.setProperty("api.coverage.report", report.toString());
    try {
      ApiCoverageReporter.main(new String[0]);
    } finally {
      System.clearProperty("api.coverage.spec");
      System.clearProperty("api.coverage.observations");
      System.clearProperty("api.coverage.report");
    }

    assertThat(report)
        .content()
        .contains("\"documented\" : 1", "\"touched\" : 1", "\"successful\" : 1");
  }
}
