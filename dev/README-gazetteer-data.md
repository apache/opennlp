<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# Gazetteer data: where it comes from and how to regenerate it

The `opennlp-geo` module reads plain text tables instead of parsing upstream distribution formats. This directory holds the scripts that derive those tables. None of the scripts are part of the build; they exist so every bundled or derivable table can be regenerated from its public source and reviewed as a plain diff.

## The three gazetteers and their data

| Gazetteer | Data source | License of the data | Shipped in the jar? |
|---|---|---|---|
| `opennlp.geo.BundledGazetteer` | Natural Earth, Populated Places theme | Public domain | Yes: `naturalearth-populated-places.txt` |
| `opennlp.geo.GeoNamesGazetteer` | GeoNames main-table extracts, downloaded by the user | CC-BY 4.0 (see the `readme.txt` alongside the downloads) | No. The user downloads the file; the CC-BY license terms, including attribution, stay with the downloaded files. |
| `opennlp.geo.OvertureGazetteer` | Overture Maps, divisions theme, flattened by a script in this directory | ODbL 1.0. Attribution and database share-alike terms apply and follow the derived table, which the user builds. | No. Nothing ODbL-licensed is distributed by the project in any form. |

No gazetteer data beyond the bundled public-domain table is added to the project; every other dataset is downloaded by the user and is not redistributed by the project. The license classifications behind this split are on record in LEGAL-732: CDLA-Permissive-2.0 was judged Category A, ODbL Category X, and public-domain/CC0 data needs only a LICENSE section. Note the theme distinction inside Overture: the Places theme is CDLA-Permissive-2.0, but the divisions theme this module consumes is ODbL, and the two must not be conflated.

## `derive-populated-places.py`

Regenerates the bundled table from the Natural Earth Populated Places GeoJSON.

```
derive-populated-places.py <ne_10m_populated_places.geojson> [output.txt]
```

1. Download `ne_10m_populated_places.geojson` from the `nvkelso/natural-earth-vector` GitHub repository, the mirror of naturalearthdata.com. The script header records the mirror commit the current table was extracted from.
2. Run the script. It needs only the Python 3 standard library.
3. The output is `naturalearth-populated-places.txt`, the semicolon-separated, pure-ASCII, LF-terminated table read by `BundledGazetteer`. Copy it over `src/main/resources/opennlp/geo/naturalearth-populated-places.txt` and review the diff.

The upstream GeoJSON carries encoding damage in some name fields (an accented letter replaced by `?` or by a wrong ASCII letter). The script detects damage by character-class artifacts and repairs it through explicit, audited replacement tables in the script source; values it cannot verify are omitted and counted, never carried. It hard-fails on any anomaly it does not recognize, so refreshing to a newer Natural Earth release forces a reviewed decision instead of silently importing new corruption.

## `derive-overture-divisions.py`

Derives a division table from an Overture Maps release, for `OvertureGazetteer`.

```
derive-overture-divisions.py <release> [output.txt] [min_locality_population]
```

for example:

```
derive-overture-divisions.py 2026-06-18.0 overture-divisions.txt 10000
```

1. Requires the `duckdb` Python package with the `httpfs` and `spatial` extensions, and network access; it reads the divisions theme (Point features of type `division`) as Parquet directly from the Overture release bucket.
2. The output is a tab-separated table, one row per division: id, primary name, comma-separated alternate names, latitude, longitude, ISO 3166-1 alpha-2 country code, subtype, population. A `#` header line carries the derivation record (release, date, filters), and rows are ordered by id so a regeneration diffs cleanly against the previous run.
3. Divisions include countries and regions, not only settlements, which is why a derived table also resolves mentions like `Australia` or `Bavaria` that place-only gazetteers miss.

## GeoNames data (no script needed)

`GeoNamesGazetteer` reads the GeoNames main table format directly, so there is nothing to derive. Download a filtered city extract (for example `cities500.zip`) from the GeoNames export dump area, unzip it, and load the `.txt` with `GeoNamesGazetteer.load(...)`. The loader indexes the whole table in memory, so use the filtered extracts rather than the full `allCountries` dump; memory grows with row and alternate-name count.

## Regeneration checklist

1. Record where the input came from: release id, mirror commit, or download date. The scripts embed this in their output headers or carry it in constants at the top of the script; update those constants when you refresh.
2. Run the script and review the whole diff of the regenerated table before committing anything.
3. If a script fails on an anomaly, that is the intended behavior: inspect the new upstream value, extend the audited repair tables only with values you have verified, and rerun.
4. Never commit downloaded upstream files, and never move an attribution-required or share-alike dataset into `src/main/resources`.
