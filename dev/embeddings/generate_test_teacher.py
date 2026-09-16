#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
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

"""Print ONNX test constants for EmbeddingTestFixtures.

Run with: uv run --with onnx==1.19.0 python dev/embeddings/generate_test_teacher.py
Python is required for regeneration, not for Maven tests. The graphs use original
numeric tables with no trained parameters or external model files.
"""

import base64
import textwrap

from onnx import TensorProto, checker, helper


def print_model(name, nodes, tensors, dimension):
    """Validate a graph and print a Java base64 constant."""
    graph = helper.make_graph(
        nodes,
        name,
        [helper.make_tensor_value_info("input_ids", TensorProto.INT64, ["batch", "tokens"])],
        [helper.make_tensor_value_info(
            "last_hidden_state", TensorProto.FLOAT, ["batch", "tokens", dimension])],
        tensors,
    )
    model = helper.make_model(graph, ir_version=8, opset_imports=[helper.make_opsetid("", 13)])
    checker.check_model(model, full_check=True)
    chunks = textwrap.wrap(base64.b64encode(model.SerializeToString()).decode("ascii"), 76)
    print(f"  private static final String {name} =")
    for index, chunk in enumerate(chunks):
        prefix = "      " if index == 0 else "          + "
        suffix = ";" if index == len(chunks) - 1 else ""
        print(f'{prefix}"{chunk}"{suffix}')


def main():
    """Generate the lookup graph and a graph with a variable output dimension."""
    # PAD, UNK, CLS, SEP, coffee, espresso, tea, history.
    table = [
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 1,
        0, 0, 0, -1,
        3, 0, 0, 0,
        2, 1, 0, 0,
        -1, 2, 0, 0,
        -1, -2, 0, 0,
    ]
    print_model("LOOKUP_TEACHER_ONNX", [
        helper.make_node("Gather", ["table", "input_ids"], ["last_hidden_state"], axis=0),
    ], [helper.make_tensor("table", TensorProto.FLOAT, [8, 4], table)], 4)

    print_model("VARIABLE_DIMENSION_ONNX", [
        helper.make_node("Cast", ["input_ids"], ["as_float"], to=TensorProto.FLOAT),
        helper.make_node("Unsqueeze", ["as_float", "axes"], ["states"]),
        helper.make_node("Shape", ["input_ids"], ["input_shape"]),
        helper.make_node("Gather", ["input_shape", "batch_axis"], ["batch_size"], axis=0),
        helper.make_node("Concat", ["ones", "batch_size"], ["repeats"], axis=0),
        helper.make_node("Tile", ["states", "repeats"], ["last_hidden_state"]),
    ], [
        helper.make_tensor("axes", TensorProto.INT64, [1], [2]),
        helper.make_tensor("batch_axis", TensorProto.INT64, [1], [0]),
        helper.make_tensor("ones", TensorProto.INT64, [2], [1, 1]),
    ], "hidden")


if __name__ == "__main__":
    main()
