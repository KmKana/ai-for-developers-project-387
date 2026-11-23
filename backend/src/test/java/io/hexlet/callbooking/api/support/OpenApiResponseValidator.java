package io.hexlet.callbooking.api.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Проверка реальных HTTP-ответов по закоммиченной OpenAPI-спецификации.
 *
 * <p>Схемы ответов берутся из {@code api/openapi/openapi.yaml} и передаются валидатору JSON Schema
 * (networknt); ожидаемые структуры в тесте не повторяются. OpenAPI 3.0-ссылки на компоненты
 * переписываются в {@code definitions} JSON Schema. Статус, отсутствующий в спецификации, считается
 * нарушением контракта.
 */
public final class OpenApiResponseValidator {

  private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

  private final ObjectMapper json = new ObjectMapper();
  private final JsonSchemaFactory schemaFactory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private final SchemaValidatorsConfig schemaConfig = formatAwareConfig();
  private final JsonNode definitions;
  private final Map<String, JsonNode> responsesByOperation = new HashMap<>();

  public OpenApiResponseValidator(Path specPath) {
    try {
      JsonNode spec = new ObjectMapper(new YAMLFactory()).readTree(specPath.toFile());
      if (spec == null || !spec.path("openapi").asText().startsWith("3.0")) {
        throw new IllegalArgumentException("Ожидается OpenAPI 3.0: " + specPath);
      }
      definitions = buildDefinitions(spec);
      spec.path("paths")
          .fields()
          .forEachRemaining(
              pathEntry ->
                  pathEntry
                      .getValue()
                      .fields()
                      .forEachRemaining(
                          methodEntry -> {
                            String method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                            if (HTTP_METHODS.contains(method)
                                && methodEntry.getValue().has("operationId")) {
                              responsesByOperation.put(
                                  methodEntry.getValue().path("operationId").asText(),
                                  methodEntry.getValue().path("responses"));
                            }
                          }));
    } catch (IOException e) {
      throw new UncheckedIOException("Не удалось прочитать " + specPath, e);
    }
  }

  public Set<String> operationIds() {
    return Set.copyOf(responsesByOperation.keySet());
  }

  public void assertConforms(String operationId, MvcResult result) {
    var response = result.getResponse();
    String contentType = response.getContentType();
    if (contentType == null || !contentType.startsWith("application/json")) {
      throw new AssertionError(
          operationId + ": ожидается application/json, получено " + contentType);
    }
    assertConforms(
        operationId,
        response.getStatus(),
        new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  public void assertConforms(String operationId, int status, String body) {
    JsonNode responses = responsesByOperation.get(operationId);
    if (responses == null) {
      throw new AssertionError("Операция " + operationId + " не найдена в OpenAPI");
    }
    JsonNode response = responses.path(String.valueOf(status));
    if (response.isMissingNode()) {
      throw new AssertionError(
          operationId
              + ": статус "
              + status
              + " не описан в OpenAPI (допустимые: "
              + responses.fieldNames()
              + ")");
    }
    JsonNode responseSchema = response.path("content").path("application/json").path("schema");
    if (responseSchema.isMissingNode()) {
      throw new AssertionError(operationId + " " + status + ": нет JSON-схемы ответа в OpenAPI");
    }

    ObjectNode schemaNode = (ObjectNode) rewriteComponentRefs(responseSchema.deepCopy());
    schemaNode.put("$schema", "http://json-schema.org/draft-07/schema#");
    schemaNode.set("definitions", definitions);
    JsonSchema schema = schemaFactory.getSchema(schemaNode, schemaConfig);

    JsonNode bodyNode;
    try {
      bodyNode = json.readTree(body);
    } catch (JsonProcessingException e) {
      throw new AssertionError(operationId + " " + status + ": тело не является JSON", e);
    }
    Set<ValidationMessage> violations = schema.validate(bodyNode);
    if (!violations.isEmpty()) {
      throw new AssertionError(
          operationId
              + " "
              + status
              + ": ответ не соответствует OpenAPI: "
              + violations
              + "\nТело: "
              + body);
    }
  }

  private static SchemaValidatorsConfig formatAwareConfig() {
    SchemaValidatorsConfig config = new SchemaValidatorsConfig();
    // format: date-time из контракта должен проверяться как RFC 3339, а не как обычная строка.
    config.setFormatAssertionsEnabled(true);
    return config;
  }

  private static JsonNode buildDefinitions(JsonNode spec) {
    ObjectNode result = new ObjectMapper().createObjectNode();
    spec.path("components")
        .path("schemas")
        .fields()
        .forEachRemaining(
            entry -> result.set(entry.getKey(), rewriteComponentRefs(entry.getValue().deepCopy())));
    return result;
  }

  private static JsonNode rewriteComponentRefs(JsonNode node) {
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      JsonNode ref = object.get("$ref");
      String prefix = "#/components/schemas/";
      if (ref != null && ref.isTextual() && ref.asText().startsWith(prefix)) {
        object.put("$ref", "#/definitions/" + ref.asText().substring(prefix.length()));
      }
      // example — аннотация OpenAPI, а не проверяемое ключевое слово JSON Schema.
      object.remove("example");
      object.fields().forEachRemaining(entry -> rewriteComponentRefs(entry.getValue()));
    } else if (node.isArray()) {
      node.forEach(OpenApiResponseValidator::rewriteComponentRefs);
    }
    return node;
  }
}
