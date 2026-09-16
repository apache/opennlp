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

"""Trains the tiny SentencePiece test models and generates the parity fixtures.

Run inside a venv with the sentencepiece package installed:
    python gen_fixtures.py <corpus.txt> <output-dir>

Every fixture line is tab-separated with backslash escaping (\\\\, \\t, \\n, \\r):
    esc(input) TAB pieceCount TAB [esc(piece) TAB id TAB begin TAB end]... TAB esc(normalized)
Offsets are UTF-16 code-unit offsets into the original input, matching Java string indexing.
"""
import sys
import sentencepiece as spm

MULTILINGUAL = [
    "Le café coûte trois euros à Paris.",
    "Der Straßenname ändert sich häufig.",
    "Ça va très bien, merci beaucoup.",
    "El niño pequeño come una manzana.",
    "Привет мир и всем добро.",
    "東京タワーに登りました。",
    "日本語の文章も少しあります。",
    "안녕하세요 세계입니다.",
    "你好世界这是中文。",
    "I love \U0001f355 and \U0001f1e9\U0001f1ea a lot!",
    "Emoji test \U0001f600 \U0001f680 ❤️ done.",
]

INPUTS = [
    "",
    " ",
    "   ",
    "a",
    "Hello world",
    " Hello   world  ",
    "Hello world.\nSecond line\ttabbed",
    "The quick brown fox jumps over the lazy dog.",
    "tokenization and segmentation",
    "Antidisestablishmentarianism",
    "water running walked faster apple book work play",
    "3.14159 x 42 = 1024?",
    "!!!???...",
    "(parentheses) and [brackets] and {braces}",
    "café naïve fiancé résumé",
    "ﬁnancial ﬂuid",
    "① ⑪ ㋿ ＫＡＴＡＫＡＮＡ",
    "ｶﾀｶﾅ half width",
    "東京タワーへ行きました",
    "日本語とEnglish混在",
    "Привет мир",
    "안녕하세요 세계",
    "你好，世界！",
    "I love \U0001f355 pizza",
    "flags \U0001f1e9\U0001f1ea \U0001f1fa\U0001f1f8 end",
    "family \U0001f469‍\U0001f469‍\U0001f467‍\U0001f466 emoji",
    "zero​width and non breaking",
    "quotes “fancy” and ‘single’ — dash",
    "<mask> the [URL] token",
    "a <mask>b[URL]c",
    "control <s> tokens </s> inline",
    "https://example.com/path?q=1&x=2",
    "UPPER lower MiXeD case",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "Ω≈ç√∫˜µ≤",
    "مرحبا بالعالم",
    "  leading and trailing  ",
    "\ttab\tstart",
    "newline\n\n\nruns",
    "mid   spaces   collapse",
]

MODELS = {
    "tiny-unigram": dict(model_type="unigram", vocab_size=300),
    "tiny-unigram-bytefb": dict(model_type="unigram", vocab_size=600, byte_fallback=True,
                                character_coverage=0.995),
    "tiny-bpe": dict(model_type="bpe", vocab_size=300),
    "tiny-unigram-identity": dict(model_type="unigram", vocab_size=300,
                                  normalization_rule_name="identity"),
    "tiny-unigram-suffix": dict(model_type="unigram", vocab_size=300,
                                treat_whitespace_as_suffix=True),
}


def esc(s):
    return (s.replace("\\", "\\\\").replace("\t", "\\t")
            .replace("\n", "\\n").replace("\r", "\\r"))


def utf16_offset(text, codepoint_offset):
    return len(text[:codepoint_offset].encode("utf-16-le")) // 2


def main(corpus, outdir):
    full_corpus = outdir + "/corpus-full.txt"
    with open(corpus, encoding="utf-8") as f:
        lines = f.read().splitlines()
    lines += MULTILINGUAL
    with open(full_corpus, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    for name, opts in MODELS.items():
        spm.SentencePieceTrainer.Train(
            input=full_corpus,
            model_prefix=outdir + "/" + name,
            hard_vocab_limit=False,
            character_coverage=opts.pop("character_coverage", 1.0),
            user_defined_symbols=["<mask>", "[URL]"],
            self_test_sample_size=10,
            **opts,
        )
        sp = spm.SentencePieceProcessor(model_file=outdir + "/" + name + ".model")
        with open(outdir + "/" + name + ".fixtures.tsv", "w", encoding="utf-8") as out:
            for text in INPUTS:
                proto = sp.EncodeAsImmutableProto(text)
                cols = [esc(text), str(len(proto.pieces))]
                for piece in proto.pieces:
                    cols += [esc(piece.piece), str(piece.id),
                             str(utf16_offset(text, piece.begin)),
                             str(utf16_offset(text, piece.end))]
                cols.append(esc(sp.Normalize(text)))
                out.write("\t".join(cols) + "\n")
        print(name, "vocab", sp.GetPieceSize())


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
