#!/usr/bin/env python3
"""Normalizes CAP United States Reports volume zips into the passage interchange file.

Input:  $LEGAL_CORPUS_HOME/raw/us/<N>.zip  (see fetch-reporter.sh)
Output: $LEGAL_CORPUS_HOME/normalized/passages.jsonl

One JSON object per line:
  {"id": "<caseid>-<opinion>-<seq>", "case": name_abbreviation,
   "cite": official citation, "date": decision_date, "vol": volume,
   "text": passage}

Each opinion's text is split on newline paragraphs and packed into passages of
roughly TARGET characters (never splitting inside a paragraph), so a passage
is a coherent run of argument the embedding pooler can work with.
"""

import json
import os
import sys
import zipfile

TARGET = 1200
HARD_MAX = 2400


def passages(text):
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    batch = []
    size = 0
    for paragraph in paragraphs:
        if batch and size + len(paragraph) > TARGET:
            yield " ".join(batch)
            batch, size = [], 0
        while len(paragraph) > HARD_MAX:
            cut = paragraph.rfind(" ", 0, HARD_MAX)
            cut = cut if cut > 0 else HARD_MAX
            yield paragraph[:cut]
            paragraph = paragraph[cut:].strip()
        if paragraph:
            batch.append(paragraph)
            size += len(paragraph) + 1
    if batch:
        yield " ".join(batch)


def official_cite(case):
    cites = case.get("citations") or []
    for cite in cites:
        if cite.get("type") == "official":
            return cite.get("cite", "")
    return cites[0].get("cite", "") if cites else ""


def main():
    home = os.environ.get(
        "LEGAL_CORPUS_HOME", os.path.expanduser("~/.cache/opennlp-legal-corpus"))
    raw = os.path.join(home, "raw", "us")
    out_dir = os.path.join(home, "normalized")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "passages.jsonl")

    zips = sorted(
        (f for f in os.listdir(raw) if f.endswith(".zip")),
        key=lambda f: int(f[:-4]))
    if not zips:
        sys.exit("no volume zips in %s; run fetch-reporter.sh first" % raw)

    cases = 0
    count = 0
    with open(out_path, "w", encoding="utf-8") as out:
        for name in zips:
            volume = name[:-4]
            with zipfile.ZipFile(os.path.join(raw, name)) as z:
                for entry in sorted(z.namelist()):
                    if not (entry.startswith("json/") and entry.endswith(".json")):
                        continue
                    case = json.loads(z.read(entry))
                    body = case.get("casebody") or {}
                    opinions = body.get("opinions") or []
                    cases += 1
                    for op_index, opinion in enumerate(opinions):
                        for seq, passage in enumerate(passages(opinion.get("text", ""))):
                            record = {
                                "id": "%s-%d-%d" % (case.get("id"), op_index, seq),
                                "case": case.get("name_abbreviation", ""),
                                "cite": official_cite(case),
                                "date": case.get("decision_date", ""),
                                "vol": volume,
                                "text": passage,
                            }
                            out.write(json.dumps(record, ensure_ascii=False) + "\n")
                            count += 1
    print("wrote %d passages from %d cases to %s" % (count, cases, out_path))


if __name__ == "__main__":
    main()
