package common.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import common.types.JsonException;

/**
 * Deserializes specific fields to specified types.
 * 
 * TODO: this does not properly deserialize field with e.g. List<String> value
 * 
 * @author woodser
 */
public class FieldDeserializer extends JsonDeserializer<Map<String, Object>> {
  
  private Map<String, Object> fieldTypes;
  
  /**
   * Constructs the deserializer with the given field names.
   * 
   * @param fieldTypes specifies the names of fields to deserialize to specific types
   */
  public FieldDeserializer(Map<String, Object> fieldTypes) {
    super();
    this.fieldTypes = fieldTypes;
  }

  @Override
  public Map<String, Object> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
    Map<String, Object> result = new HashMap<String, Object>();
    jp.nextToken();
    while (!JsonToken.END_OBJECT.equals(jp.getCurrentToken())) {
      String tokenText = jp.getText();
      jp.nextToken();
      Object type = fieldTypes.get(tokenText);
      if (type != null) {
        if (type instanceof Class) result.put(tokenText, jp.readValueAs((Class<?>) type));
        else if (type instanceof TypeReference) result.put(tokenText, jp.readValueAs((TypeReference<?>) type));
        else throw new JsonException("Invalid deserialization type " + type.getClass() + " for field '" + tokenText + "'");
      } else {
        
        
        if (JsonToken.START_OBJECT.equals(jp.getCurrentToken())) {
          result.put(tokenText, deserialize(jp, ctxt));
        } else if (JsonToken.START_ARRAY.equals(jp.getCurrentToken())) {
          jp.nextToken();
          List<Object> list = new ArrayList<Object>();
          while (!JsonToken.END_ARRAY.equals(jp.getCurrentToken())) {
            list.add(deserialize(jp, ctxt));
            jp.nextToken();
          }
          result.put(tokenText, list);
        } else {
          result.put(tokenText, jp.readValueAs(Object.class));
        }
      }
      jp.nextToken();
    }
    return result;
  }
}
