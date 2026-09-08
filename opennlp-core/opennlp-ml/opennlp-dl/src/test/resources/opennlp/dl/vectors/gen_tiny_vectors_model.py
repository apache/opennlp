# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Generates tiny-vectors.onnx, the deterministic model behind SentenceVectorsDLEmbedderTest.
#
# The graph computes output[b][t][d] = float(input_ids[b][t]) * W[0][d] with
# W = [[0.5, -1.0, 2.0]], so the vector at any token position is that token's vocabulary id
# times W, hand-computable in the test. It declares the same three inputs a BERT-style
# encoder declares (input_ids, attention_mask, token_type_ids; the latter two are accepted
# and ignored) and one output of shape [batch, tokens, 3] so the hidden dimension is static
# in the model metadata.
#
# Regenerate with: python3 gen_tiny_vectors_model.py   (requires the onnx package)

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

W = np.array([[0.5, -1.0, 2.0]], dtype=np.float32)

cast = helper.make_node("Cast", ["input_ids"], ["ids_float"], to=TensorProto.FLOAT)
unsqueeze = helper.make_node("Unsqueeze", ["ids_float", "axes"], ["ids_3d"])
matmul = helper.make_node("MatMul", ["ids_3d", "w"], ["last_hidden_state"])


def encoder_input(name):
    return helper.make_tensor_value_info(name, TensorProto.INT64, ["batch", "tokens"])


graph = helper.make_graph(
    [cast, unsqueeze, matmul],
    "tiny-vectors",
    [encoder_input("input_ids"), encoder_input("attention_mask"),
     encoder_input("token_type_ids")],
    [helper.make_tensor_value_info(
        "last_hidden_state", TensorProto.FLOAT, ["batch", "tokens", 3])],
    [numpy_helper.from_array(np.array([2], dtype=np.int64), name="axes"),
     numpy_helper.from_array(W, name="w")],
)

model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
model.ir_version = 8
onnx.checker.check_model(model)
onnx.save(model, "tiny-vectors.onnx")
print("wrote tiny-vectors.onnx,", len(model.SerializeToString()), "bytes")
