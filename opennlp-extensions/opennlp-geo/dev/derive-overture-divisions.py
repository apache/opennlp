#!/usr/bin/env python3
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
"""Derives a division gazetteer table from an Overture Maps release.

Usage:  derive-overture-divisions.py <release> [output.txt] [min_locality_population]
        e.g. derive-overture-divisions.py 2026-06-18.0 overture-divisions.txt 10000

Input:  the divisions theme, type=division (Point features), read as Parquet directly
        from the Overture release bucket. Requires the duckdb Python package with the
        httpfs and spatial extensions, and network access. The divisions theme is published
        under the Open Database License (ODbL), which requires attribution and applies
        share-alike terms to derivative databases, so the derived table carries those
        terms too; check and record the license and attribution requirements of the
        release you pull.
Output: the tab-separated table read by opennlp.geo.OvertureGazetteer, one row per
        division: id, primary name, comma-separated alternate names, latitude,
        longitude, ISO 3166-1 alpha-2 country code, subtype, population. A '#' header
        carries the derivation record. Rows are ordered by id so regeneration diffs
        stay reviewable.

Kept subtypes: country, dependency, region, county, localadmin, locality. Localities
below the population floor are dropped to keep the table bundle-sized; administrative
subtypes are always kept. The script hard-fails if the release schema does not carry
the expected columns, so a schema change forces a reviewed decision instead of
importing surprises. Verify the names/geometry column shapes against the release you
pull; this script was written against the documented divisions schema (names.primary
plus optional common-name translations, Point geometry, country, subtype, population).
"""
import datetime
import sys

import duckdb

KEPT_SUBTYPES = ("country", "dependency", "region", "county", "localadmin", "locality")

# The separator between the fields of one output row.
FIELD_SEPARATOR = "\t"


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    release = sys.argv[1]
    output = sys.argv[2] if len(sys.argv) > 2 else "overture-divisions.txt"
    min_population = int(sys.argv[3]) if len(sys.argv) > 3 else 10000

    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs; INSTALL spatial; LOAD spatial;")
    con.execute("SET s3_region='us-west-2';")
    source = (
        "s3://overturemaps-us-west-2/release/"
        f"{release}/theme=divisions/type=division/*"
    )
    subtypes = ", ".join(f"'{s}'" for s in KEPT_SUBTYPES)
    rows = con.execute(
        f"""
        SELECT id,
               names.primary AS name,
               COALESCE(map_values(names.common), []) AS alternates,
               ST_Y(ST_GeomFromWKB(geometry)) AS latitude,
               ST_X(ST_GeomFromWKB(geometry)) AS longitude,
               COALESCE(country, '') AS country,
               subtype,
               COALESCE(population, 0) AS population
        FROM read_parquet('{source}', filename=true, hive_partitioning=1)
        WHERE subtype IN ({subtypes})
          AND (subtype != 'locality' OR COALESCE(population, 0) >= {min_population})
        ORDER BY id
        """
    ).fetchall()
    if not rows:
        sys.exit(f"no divisions read from {source}; check the release id and schema")

    kept = 0
    with open(output, "w", encoding="utf-8", newline="\n") as out:
        out.write("# Division gazetteer table, authored by the Apache OpenNLP project as a\n")
        out.write("# derivation of Overture Maps divisions (read by opennlp.geo.OvertureGazetteer).\n")
        out.write(f"# Derivation record: release {release}, subtypes {','.join(KEPT_SUBTYPES)},\n")
        out.write(f"# locality population floor {min_population}, derived {datetime.date.today()}\n")
        out.write("# by dev/derive-overture-divisions.py. Upstream license: ODbL, attribution and\n")
        out.write("# database share-alike terms apply to this derived table (verify against the\n")
        out.write("# licensing documentation of the release named above).\n")
        for row in rows:
            (division_id, name, alternates, lat, lon, country, subtype, population) = row
            if not name or lat is None or lon is None:
                continue  # a division without a name or point cannot be looked up
            clean_alternates = ",".join(
                sorted({a.strip() for a in alternates if a and a.strip() and a.strip() != name})
            )
            fields = (
                str(division_id), name, clean_alternates, f"{lat:.5f}", f"{lon:.5f}",
                country, subtype, str(int(population)),
            )
            for field in fields:
                if FIELD_SEPARATOR in field or "\n" in field:
                    sys.exit(f"field contains a separator, refusing to emit: {fields}")
            out.write(FIELD_SEPARATOR.join(fields) + "\n")
            kept += 1
    print(f"wrote {kept} divisions to {output}")


if __name__ == "__main__":
    main()
