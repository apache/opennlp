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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class DocumentCategorizerConfigTest {

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
}
