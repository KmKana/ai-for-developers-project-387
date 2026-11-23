package io.hexlet.callbooking.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Контракт должен явно описывать коды ошибок для GET /api/slots и общий тип ApiErrorCode. */
class SlotContractDocumentationTest {

  @Test
  void slotOperationDocumentsBothBadRequestCodes() throws IOException {
    String yaml = readSpec();

    int start = yaml.indexOf("  /api/slots:");
    assertThat(start).isPositive();
    int nextPath = yaml.indexOf("\n  /api/", start + 1);
    int components = yaml.indexOf("\ncomponents:", start + 1);
    int end = nextPath > 0 ? nextPath : components;
    assertThat(end).isPositive();
    String slotOperation = yaml.substring(start, end);

    assertThat(slotOperation).contains("VALIDATION_ERROR");
    assertThat(slotOperation).contains("MEETING_TYPE_NOT_FOUND");
  }

  @Test
  void apiErrorCodeDocumentsInternalServerError() throws IOException {
    String yaml = readSpec();

    int start = yaml.indexOf("    ApiErrorCode:");
    assertThat(start).isPositive();
    Matcher nextSchema = Pattern.compile("\n    [A-Za-z]").matcher(yaml);
    int end = nextSchema.find(start + 1) ? nextSchema.start() : yaml.length();
    String errorCodeSchema = yaml.substring(start, end);

    assertThat(errorCodeSchema).contains("- INTERNAL_ERROR");
    assertThat(errorCodeSchema).doesNotContain("SLOT_TAKEN", "OVERLAP");
  }

  private static String readSpec() throws IOException {
    Path spec = Path.of("..", "api", "openapi", "openapi.yaml");
    if (!Files.exists(spec)) {
      spec = Path.of("api", "openapi", "openapi.yaml");
    }
    return Files.readString(spec);
  }
}
