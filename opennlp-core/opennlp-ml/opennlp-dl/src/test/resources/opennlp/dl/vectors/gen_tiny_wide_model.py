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

# Generates tiny-wide.onnx, the model behind SentenceVectorsDLDirectMemoryTest.
#
# The graph is the one of tiny-vectors.onnx with a hidden dimension of 1024: output[b][t][d] =
# float(input_ids[b][t]) * W[0][d] with W[0][d] = (d % 5 - 2) / 2. The output is declared as
# [batch, tokens, hidden] with a symbolic hidden dimension, so SentenceVectorsDL has to measure
# the dimension with one inference instead of reading it from the model metadata.
#
# Regenerate with: python3 gen_tiny_wide_model.py   (requires the onnx package)

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

HIDDEN = 1024
W = (((np.arange(HIDDEN) % 5) - 2) / 2).astype(np.float32).reshape(1, HIDDEN)

cast = helper.make_node("Cast", ["input_ids"], ["ids_float"], to=TensorProto.FLOAT)
unsqueeze = helper.make_node("Unsqueeze", ["ids_float", "axes"], ["ids_3d"])
matmul = helper.make_node("MatMul", ["ids_3d", "w"], ["last_hidden_state"])


def encoder_input(name):
    return helper.make_tensor_value_info(name, TensorProto.INT64, ["batch", "tokens"])


graph = helper.make_graph(
    [cast, unsqueeze, matmul],
    "tiny-wide",
    [encoder_input("input_ids"), encoder_input("attention_mask"),
     encoder_input("token_type_ids")],
    [helper.make_tensor_value_info(
        "last_hidden_state", TensorProto.FLOAT, ["batch", "tokens", "hidden"])],
    [numpy_helper.from_array(np.array([2], dtype=np.int64), name="axes"),
     numpy_helper.from_array(W, name="w")],
)

model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
model.ir_version = 8
onnx.checker.check_model(model)
onnx.save(model, "tiny-wide.onnx")
print("wrote tiny-wide.onnx,", len(model.SerializeToString()), "bytes")
