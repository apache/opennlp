#!/usr/bin/env python3
"""Normalizes the raw Bouvier per-letter HTML into the dictionary interchange file.

Input:  $LEGAL_CORPUS_HOME/raw/bouvier/bouvier_[a-z].htm  (see fetch-dictionary.sh)
Output: $LEGAL_CORPUS_HOME/normalized/dictionary.tsv

One line per entry: HEADWORD <TAB> definition. An entry is a paragraph whose
first bold run is the headword; following paragraphs without a leading bold
run (numbered clauses of the same entry) are appended to its definition.
Headwords keep their original casing (upper case, may contain spaces, periods,
hyphens, and apostrophes, e.g. "HABEAS CORPUS", "A QUO", "NE EXEAT").
Tabs and newlines inside definitions are collapsed to single spaces.
"""

import os
import sys
from html.parser import HTMLParser


class BouvierParser(HTMLParser):
    """Collects (headword, definition) pairs from one per-letter file."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.entries = []
        self.in_p = False
        self.in_b = False
        self.p_bold = None
        self.p_text = []
        self.saw_text_before_bold = False

    def handle_starttag(self, tag, attrs):
        if tag == "p":
            self._flush_paragraph()
            self.in_p = True
        elif tag == "b" and self.in_p:
            self.in_b = True

    def handle_endtag(self, tag):
        if tag == "p":
            self._flush_paragraph()
            self.in_p = False
        elif tag == "b":
            self.in_b = False

    def handle_data(self, data):
        if not self.in_p:
            return
        if self.in_b and self.p_bold is None and not self.saw_text_before_bold:
            self.p_bold = data.strip()
        else:
            if data.strip():
                if self.p_bold is None:
                    self.saw_text_before_bold = True
                self.p_text.append(data)

    def _flush_paragraph(self):
        text = " ".join(" ".join(self.p_text).split())
        text = text.lstrip(" ,.;:-")
        if self.p_bold is not None and self._plausible_headword(self.p_bold):
            self.entries.append([self.p_bold, text])
        elif self.entries and text:
            self.entries[-1][1] = (self.entries[-1][1] + " " + text).strip()
        self.p_bold = None
        self.p_text = []
        self.saw_text_before_bold = False

    @staticmethod
    def _plausible_headword(word):
        word = word.strip()
        if not word or len(word) > 60:
            return False
        if word != word.upper():
            return False
        if "BOUVIER" in word or "DICTIONARY" in word:
            return False
        return all(c.isalnum() or c in " '.,&-" for c in word)


def clean_headword(word):
    return " ".join(word.strip().rstrip(" ,.;:").split())


def main():
    home = os.environ.get(
        "LEGAL_CORPUS_HOME", os.path.expanduser("~/.cache/opennlp-legal-corpus"))
    raw = os.path.join(home, "raw", "bouvier")
    out_dir = os.path.join(home, "normalized")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "dictionary.tsv")

    files = sorted(f for f in os.listdir(raw) if f.endswith(".htm"))
    if not files:
        sys.exit("no raw files in %s; run fetch-dictionary.sh first" % raw)

    count = 0
    seen = set()
    with open(out_path, "w", encoding="utf-8") as out:
        for name in files:
            parser = BouvierParser()
            with open(os.path.join(raw, name), encoding="latin-1") as f:
                parser.feed(f.read())
            parser._flush_paragraph()
            for headword, definition in parser.entries:
                headword = clean_headword(headword)
                definition = " ".join(definition.split())
                if not headword or len(definition) < 20:
                    continue
                key = headword.lower()
                if key in seen:
                    continue
                seen.add(key)
                out.write("%s\t%s\n" % (headword, definition))
                count += 1
    print("wrote %d entries to %s" % (count, out_path))


if __name__ == "__main__":
    main()
