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
# Generates tiny-pooled.onnx, the model behind the pooled-output tests of SentenceVectorsDL.
#
# The graph declares the three BERT-style inputs and two outputs, in this order:
#   token_embeddings[b][t]   = float(input_ids[b][t]) * W          shape [batch, tokens, 3]
#   sentence_embedding[b]    = sum over t of token_embeddings[b][t] shape [batch, 3]
# with W = [[0.5, -1.0, 2.0]], so the pooled output of an input is the sum of its vocabulary ids
# times W. The pooled output is not the first output, so the tests show it is selected by name.
#
# Uses only the Python standard library. Regenerate with: python3 gen_tiny_pooled_model.py

import array
import struct


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def key(field, wire):
    return varint((field << 3) | wire)


def num(field, n):
    return key(field, 0) + varint(n)


def raw(field, b):
    if isinstance(b, str):
        b = b.encode()
    return key(field, 2) + varint(len(b)) + b


def tensor(name, dims, dtype, data):
    return b"".join(num(1, d) for d in dims) + num(2, dtype) + raw(8, name) + raw(9, data)


def node(op, inputs, outputs, attrs=()):
    return (b"".join(raw(1, i) for i in inputs) + b"".join(raw(2, o) for o in outputs)
            + raw(4, op) + b"".join(raw(5, a) for a in attrs))


def attr_int(name, value):
    return raw(1, name) + num(3, value) + num(20, 2)


def value_info(name, elem, dims):
    shape = b"".join(raw(1, num(1, d) if isinstance(d, int) else raw(2, d)) for d in dims)
    return raw(1, name) + raw(2, raw(1, num(1, elem) + raw(2, shape)))


FLOAT, INT64 = 1, 7
nodes = [
    node("Cast", ["input_ids"], ["ids_float"], [attr_int("to", FLOAT)]),
    node("Unsqueeze", ["ids_float", "axes2"], ["ids_3d"]),
    node("MatMul", ["ids_3d", "w"], ["token_embeddings"]),
    node("ReduceSum", ["token_embeddings", "axes1"], ["sentence_embedding"],
         [attr_int("keepdims", 0)]),
]
initializers = [
    tensor("axes2", [1], INT64, array.array("q", [2]).tobytes()),
    tensor("axes1", [1], INT64, array.array("q", [1]).tobytes()),
    tensor("w", [1, 3], FLOAT, struct.pack("<3f", 0.5, -1.0, 2.0)),
]
inputs = [value_info(n, INT64, ["batch", "tokens"])
          for n in ("input_ids", "attention_mask", "token_type_ids")]
outputs = [value_info("token_embeddings", FLOAT, ["batch", "tokens", 3]),
           value_info("sentence_embedding", FLOAT, ["batch", 3])]
graph = (b"".join(raw(1, n) for n in nodes) + raw(2, "tiny-pooled")
         + b"".join(raw(5, t) for t in initializers)
         + b"".join(raw(11, i) for i in inputs) + b"".join(raw(12, o) for o in outputs))
model = num(1, 8) + raw(8, raw(1, "") + num(2, 13)) + raw(7, graph)
with open("tiny-pooled.onnx", "wb") as f:
    f.write(model)
print("wrote tiny-pooled.onnx,", len(model), "bytes")
