package io.hexlet.callbooking.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Принимает только JSON-целое: контракт объявляет длительность как int32. */
public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
      return parser.getIntValue();
    }
    return (Integer) context.handleUnexpectedToken(Integer.class, parser);
  }
}
