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

"""Generates parity fixtures for pre-trained real-world models (no training).

Usage: python gen_real_fixtures.py <model-dir>
Reads every *.model in the directory and writes a sibling *.fixtures.tsv in the same
escaped-TSV format as gen_fixtures.py, over a larger and messier input list.
"""
import glob
import os
import sys
import sentencepiece as spm
from gen_fixtures import INPUTS, esc, utf16_offset

EXTRA = [
    "The Transformer architecture revolutionized natural language processing in 2017.",
    "supercalifragilisticexpialidocious and pneumonoultramicroscopicsilicovolcanoconiosis",
    "e=mc^2, F=ma, and a^2+b^2=c^2 are famous equations.",
    "Mixed scripts: English, 日本語, 한국어, русский, and العربية together.",
    "Prices: $19.99, €25,50, £12, ¥1500, and ₹999.",
    "C++ and C# and F# are programming languages; so is Java.",
    "def encode(text): return sp.encode(text, out_type=str)",
    "SELECT * FROM documents WHERE score > 0.5 ORDER BY rank;",
    "The 2024 Summer Olympics were held in Paris, France.",
    "COVID-19 vaccines use mRNA technology (Pfizer-BioNTech, Moderna).",
    "Email me at test.user+tag@example.co.uk or call +1 (555) 010-9999.",
    "10,000 steps a day keeps the doctor away... allegedly!",
    "The naive resume of the fiancee included a cafe visit.",
    "¿Dónde está la biblioteca? ¡Allí está!",
    "Smørrebrød og æbleskiver er danske specialiteter.",
    "Zażółć gęślą jaźń is a Polish pangram.",
    "Đây là tiếng Việt với nhiều dấu.",
    "今日はいい天気ですね。明日も晴れるといいな。",
    "北京和上海都是大城市。",
    "한국의 수도는 서울입니다.",
    "\U0001f9d1‍\U0001f4bb codes while \U0001f9d1‍\U0001f373 cooks \U0001f35c!",
    " line separator and  paragraph separator lurk here",
    "﻿BOM at the start of this sentence",
    "tabs\tand\ttabs\tand\ttabs",
    "CRLF\r\nline endings\r\nhappen",
]


def main(model_dir):
    for model_path in sorted(glob.glob(os.path.join(model_dir, "*.model"))):
        name = os.path.splitext(model_path)[0]
        sp = spm.SentencePieceProcessor(model_file=model_path)
        with open(name + ".fixtures.tsv", "w", encoding="utf-8") as out:
            for text in INPUTS + EXTRA:
                proto = sp.EncodeAsImmutableProto(text)
                cols = [esc(text), str(len(proto.pieces))]
                for piece in proto.pieces:
                    cols += [esc(piece.piece), str(piece.id),
                             str(utf16_offset(text, piece.begin)),
                             str(utf16_offset(text, piece.end))]
                cols.append(esc(sp.Normalize(text)))
                out.write("\t".join(cols) + "\n")
        print(os.path.basename(name), "vocab", sp.GetPieceSize())


if __name__ == "__main__":
    main(sys.argv[1])
