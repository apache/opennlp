/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.dl.doccat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class DocumentCategorizerConfigTest {

  static Stream<Arguments> id2labels() {
    return Stream.of(
        Arguments.of("{\"id2label\": {\"0\": \"x\"}}", Map.of("0", "x")),
        Arguments.of("{\"id2label\":{}}", Map.of()),
        Arguments.of("{\"id2label\" : {\n\"0\" : \"neg\" ,\n\"1\"\t:\r\n\"pos\"\n}}",
            Map.of("0", "neg", "1", "pos")),
        // no id2label member, regardless of the other members
        Arguments.of("{\"vocab_size\": 5}", Map.of()),
        Arguments.of("{\"label2id\": {\"x\": 0}, \"my_id2label\": {\"0\": \"x\"}}", Map.of()),
        Arguments.of("{\"note\": \"the \\\"id2label\\\" map\"}", Map.of()),
        // only the top-level member counts
        Arguments.of("{\"other\": {\"id2label\": {\"0\": \"x\"}}}", Map.of()),
        // a later member with the same key overwrites the earlier one
        Arguments.of("{\"id2label\": {\"0\": \"x\"}, \"id2label\": {\"0\": \"y\"}}",
            Map.of("0", "y")),
        Arguments.of("{\"id2label\": {\"0\": \"x\", \"0\": \"y\"}}", Map.of("0", "y")),
        // escapes in keys and labels are decoded
        Arguments.of("{\"id2label\": {\"0\": \"say \\\"hi\\\"\", \"1\": \"ok\"}}",
            Map.of("0", "say \"hi\"", "1", "ok")),
        Arguments.of("{\"id2label\": {\"a\\\"b\": \"c\\\\d\"}}", Map.of("a\"b", "c\\d")),
        Arguments.of("{\"id2label\": {\"0\": \"\\u00e9\\uD83D\\uDE00\"}}", Map.of("0", "\u00E9\uD83D\uDE00")),
        // a raw line break or tab inside a label is content
        Arguments.of("{\"id2label\": {\"0\": \"li\nne\", \"1\": \"tab\there\"}}",
            Map.of("0", "li\nne", "1", "tab\there")),
        Arguments.of("{\"id2label\": {\"k\ney\": \"v\"}}", Map.of("k\ney", "v")),
        // a brace inside a label does not end the object
        Arguments.of("{\"id2label\": {\"0\": \"x\", \"1\": \"y}\", \"2\": \"z\"}}",
            Map.of("0", "x", "1", "y}", "2", "z")),
        Arguments.of("{\"id2label\": {\"\": \"x\", \"0\":\"\"}}", Map.of("", "x", "0", "")),
        Arguments.of("{\"id2label\": {\"\uD83D\uDE00\": \"\uD801\uDC12\", \"\u00E9\": \"\u3000x\"}}",
            Map.of("\uD83D\uDE00", "\uD801\uDC12", "\u00E9", "\u3000x")));
  }

  @ParameterizedTest
  @MethodSource("id2labels")
  public void testId2LabelsFromJson(String json, Map<String, String> expected) {
    assertEquals(expected, DocumentCategorizerConfig.fromJson(json).id2label());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // id2label is not an object, or a label is not a string
      "{\"id2label\": \"nope\"}", "{\"id2label\": [\"0\", \"x\"]}", "{\"id2label\": null}",
      "{\"id2label\": {\"0\": 5}}", "{\"id2label\": {\"0\": {\"n\": \"y\"}}}",
      "{\"id2label\": {\"0\": null}}",
      // malformed text at any position of the configuration
      "{\"id2label\": {\"0\": \"x\"", "{\"id2label\": {\"0\": \"x\" \"1\": \"y\"}}",
      "{\"id2label\"id2label\": {\"0\": \"x\"}}", "{\"id2label\":\u00A0{\"0\": \"x\"}}",
      "{\"id2label\": {\"0\": \"x\\q\"}}", "{\"hidden_size\": 768,}", "[]", "x"})
  public void testId2LabelsFromJsonRejectsMalformedText(String json) {
    assertThrows(IllegalArgumentException.class, () -> DocumentCategorizerConfig.fromJson(json));
  }

  @Test
  public void testId2LabelsFromJsonNullThrows() {
    assertThrows(IllegalArgumentException.class, () -> DocumentCategorizerConfig.fromJson(null));
  }

  @Test
  public void testId2LabelsFromJsonMessageNamesTheKey() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentCategorizerConfig.fromJson("{\"id2label\": {\"0\": \"x\", \"1\": 2}}"));
    assertTrue(e.getMessage().contains("\"1\""), e.getMessage());
  }

  @Test
  public void testId2LabelsFromJsonPrettyValid() {
    final String json = """
        {
          "_num_labels": 5,
          "architectures": [
            "BertForSequenceClassification"
          ],
          "attention_probs_dropout_prob": 0.1,
          "directionality": "bidi",
          "finetuning_task": "sentiment-analysis",
          "hidden_act": "gelu",
          "hidden_dropout_prob": 0.1,
          "hidden_size": 768,
          "id2label": {
            "0": "1 star",
            "1": "2 stars",
            "2": "3 stars",
            "3": "4 stars",
            "4": "5 stars"
          },
          "initializer_range": 0.02,
          "intermediate_size": 3072,
          "label2id": {
            "1 star": 0,
            "2 stars": 1,
            "3 stars": 2,
            "4 stars": 3,
            "5 stars": 4
          },
          "layer_norm_eps": 1e-12,
          "max_position_embeddings": 512,
          "model_type": "bert",
          "num_attention_heads": 12,
          "num_hidden_layers": 12,
          "output_past": true,
          "pad_token_id": 0,
          "pooler_fc_size": 768,
          "pooler_num_attention_heads": 12,
          "pooler_num_fc_layers": 3,
          "pooler_size_per_head": 128,
          "pooler_type": "first_token_transform",
          "type_vocab_size": 2,
          "vocab_size": 105879
        }
        """;

    final DocumentCategorizerConfig config = DocumentCategorizerConfig.fromJson(json);
    assertNotNull(config);
    final Map<String, String> map = config.id2label();
    assertEquals(5, map.size());
    assertEquals("1 star", map.get("0"));
    assertEquals("2 stars", map.get("1"));
    assertEquals("3 stars", map.get("2"));
    assertEquals("4 stars", map.get("3"));
    assertEquals("5 stars", map.get("4"));
  }

  @Test
  public void testId2LabelsFromJsonUglyValid() {
    final String json = """
        {"_num_labels":5,"architectures":["BertForSequenceClassification"],"attention_probs_
        dropout_prob":0.1,"directionality":"bidi","finetuning_task":"sentiment-analysis",
        "hidden_act":"gelu","hidden_dropout_prob":0.1,"hidden_size":768,"id2label":{"0":"1 star",
        "1":"2 stars","2":"3 stars","3":"4 stars","4":"5 stars"},"initializer_range":0.02,
        "intermediate_size":3072,"label2id":{"1 star":0,"2 stars":1,"3 stars":2,"4 stars":3,"5
         stars":4},"layer_norm_eps":1e-12,"max_position_embeddings":512,"model_type":"bert",
        "num_attention_heads":12,"num_hidden_layers":12,"output_past":true,"pad_token_id":0,"
        pooler_fc_size":768,"pooler_num_attention_heads":12,"pooler_num_fc_layers":3,
        "pooler_size_per_head":128,"pooler_type":"first_token_transform","type_vocab_size":2,
        "vocab_size":105879}
        """;

    final DocumentCategorizerConfig config = DocumentCategorizerConfig.fromJson(json);
    assertNotNull(config);
    final Map<String, String> map = config.id2label();
    assertEquals(5, map.size());
    assertEquals("1 star", map.get("0"));
    assertEquals("2 stars", map.get("1"));
    assertEquals("3 stars", map.get("2"));
    assertEquals("4 stars", map.get("3"));
    assertEquals("5 stars", map.get("4"));
  }

  @Test
  public void testId2LabelsFromJsonNoValues() {
    final String json = """
        {"_num_labels":5,"architectures":["BertForSequenceClassification"],"attention_probs
        _dropout_prob":0.1,"directionality":"bidi","finetuning_task":"sentiment-analysis",
        "hidden_act":"gelu","hidden_dropout_prob":0.1,"hidden_size":768,"layer_norm_eps":1e-12,
        "max_position_embeddings":512,"model_type":"bert",
        "num_attention_heads":12,"num_hidden_layers":12,"output_past":true,"pad_token_id":0,
        "pooler_fc_size":768,"pooler_num_attention_heads":12,"pooler_num_fc_layers":3,
        "pooler_size_per_head":128,"pooler_type":"first_token_transform","type_vocab_size":2,
        "vocab_size":105879}
        """;

    final DocumentCategorizerConfig config = DocumentCategorizerConfig.fromJson(json);
    assertNotNull(config);
    assertEquals(0, config.id2label().size());
  }

  @Test
  public void testId2LabelsFromJsonEmptyInput() {
    final String json = "";
    final DocumentCategorizerConfig config = DocumentCategorizerConfig.fromJson(json);
    assertNotNull(config);
    assertEquals(0, config.id2label().size());
  }

  @Test
  public void testId2LabelsFromJsonPrettyIdIsNotANumberValid() {
    final String json = """
        {
          "_num_labels": 5,
          "architectures": [
            "BertForSequenceClassification"
          ],
          "attention_probs_dropout_prob": 0.1,
          "directionality": "bidi",
          "finetuning_task": "sentiment-analysis",
          "hidden_act": "gelu",
          "hidden_dropout_prob": 0.1,
          "hidden_size": 768,
          "id2label": {
            "a0": "1 star",
            "a1": "2 stars",
            "a2": "3 stars",
            "a3": "4 stars",
            "a4": "5 stars"
          },
          "initializer_range": 0.02,
          "intermediate_size": 3072,
          "label2id": {
            "1 star": "a0",
            "2 stars": "a1",
            "3 stars": "a2",
            "4 stars": "a3",
            "5 stars": "a4"
          },
          "layer_norm_eps": 1e-12,
          "max_position_embeddings": 512,
          "model_type": "bert",
          "num_attention_heads": 12,
          "num_hidden_layers": 12,
          "output_past": true,
          "pad_token_id": 0,
          "pooler_fc_size": 768,
          "pooler_num_attention_heads": 12,
          "pooler_num_fc_layers": 3,
          "pooler_size_per_head": 128,
          "pooler_type": "first_token_transform",
          "type_vocab_size": 2,
          "vocab_size": 105879
        }
        """;

    final DocumentCategorizerConfig config = DocumentCategorizerConfig.fromJson(json);
    assertNotNull(config);
    final Map<String, String> map = config.id2label();
    assertEquals(5, map.size());
    assertEquals("1 star", map.get("a0"));
    assertEquals("2 stars", map.get("a1"));
    assertEquals("3 stars", map.get("a2"));
    assertEquals("4 stars", map.get("a3"));
    assertEquals("5 stars", map.get("a4"));
  }

  @Test
  public void testId2LabelsFromJsonSkipsALeadingByteOrderMark() {
    assertEquals(Map.of("0", "x"),
        DocumentCategorizerConfig.fromJson("﻿{\"id2label\": {\"0\": \"x\"}}").id2label());
  }
}
