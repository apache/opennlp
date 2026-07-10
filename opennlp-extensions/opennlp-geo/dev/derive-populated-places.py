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
"""Derives the bundled gazetteer table from Natural Earth Populated Places.

Usage:  derive-populated-places.py <ne_10m_populated_places.geojson> [output.txt]

Input:  ne_10m_populated_places.geojson from the Natural Earth GitHub distribution
        (nvkelso/natural-earth-vector, a mirror of naturalearthdata.com; public domain).
Output: naturalearth-populated-places.txt, the project-authored semicolon table read by
        opennlp.geo.BundledGazetteer. Pure ASCII, LF line endings.

The upstream distribution itself carries encoding damage (mojibake) in some fields: an
accented letter replaced by '?' or by a wrong ASCII letter (for example ADM1NAME 'MUdenine'
for Medenine, NAMEASCII 'AmundseniScott South Pole Station' for Amundsen-Scott). This script
detects the damage by character-class artifacts ('?', U+FFFD, an uppercase letter inside a
lowercase word) and by the explicit, audited repair tables below, which were built by
comparing every suspect value against its accent-bearing upstream siblings and the
documented place names. Values that cannot be verified are omitted and counted, never
carried. The script hard-fails on any anomaly it does not recognize, so refreshing to a
newer Natural Earth release forces a reviewed decision instead of importing new corruption.
"""
import json
import re
import sys
import unicodedata

EXTRACTION_DATE = '2026-07-06'
MIRROR_COMMIT = '789c9904087846cc3361302857aa2e76b0ae71ff'

# Letters NFD mark-stripping cannot reduce to ASCII; the standard transliterations.
# Written as escapes so this file stays pure ASCII.
TRANSLIT = {
    '\u00D0': 'D', '\u00F0': 'd',    # Eth
    '\u00DE': 'Th', '\u00FE': 'th',  # Thorn
    '\u00C6': 'AE', '\u00E6': 'ae',  # Ash
    '\u0152': 'OE', '\u0153': 'oe',  # Ethel
    '\u00D8': 'O', '\u00F8': 'o',    # O with stroke
    '\u00DF': 'ss',                  # Sharp s
    '\u0110': 'D', '\u0111': 'd',    # D with stroke
    '\u0141': 'L', '\u0142': 'l',    # L with stroke
    '\u0131': 'i',                   # Dotless i
    '\u2013': '-', '\u2014': '-',    # En and em dash to hyphen-minus
    '\u2019': "'",                   # Right single quotation mark to apostrophe
}

# Canonical-name corrections for records whose NAME and NAMEASCII are both damaged upstream,
# keyed by NE_ID. Verified against the record's own admin-1 value and external identifiers.
NAME_CORRECTIONS = {
    # Upstream NAME and NAMEASCII are both 'Sdid Bouzid'; ADM1NAME is 'Sidi Bou Zid' and
    # WIKIDATAID is Q692716 (Sidi Bouzid, Tunisia).
    1159112843: 'Sidi Bouzid',
}

# Alternate-name repairs for damaged values in NAMEASCII, NAMEALT, NAMEPAR, or MEGANAME,
# keyed by the exact damaged value.
ALT_REPAIRS = {
    'EdDamer': 'Ed Damer',                       # NE_ID 1159147973, NAME 'Ad-Damir'
    'Hai PhNng': 'Hai Phong',                    # NE_ID 1159149349 (MEGANAME), NAME 'Haiphong'
    'Hai Phnng': 'Hai Phong',                    # NE_ID 1159149349 (NAMEALT), same damage
    'MMabatho (Mafikeng)': 'Mmabatho (Mafikeng)',  # NE_ID 1159135963
}

# A second alternate-name damage class needs no table: a value that matches the derived
# canonical name except for exactly one character at a position that came from a non-ASCII
# character of NAME is a corruption of the primary name itself (upstream MEGANAME 'Zarich'
# for Zurich, NAMEASCII 'AmundseniScott South Pole Station' for Amundsen-Scott). Such a
# value is dropped and counted; see damaged_variant_of below.

# Admin-1 (containment) repairs, keyed by (ADM0NAME, damaged folded value). Every entry was
# verified against the accent-bearing sibling value of the same admin-1 region elsewhere in
# the dataset, or against the documented region name where no clean sibling exists.
ADM1_REPAIRS = {
    ('Algeria', 'BZchar'): 'Bechar',
    ('Algeria', 'TZbessa'): 'Tebessa',
    ('Algeria', 'SZtif'): 'Setif',
    ('Angola', 'BiO'): 'Bie',
    ('Angola', 'HuOla'): 'Huila',
    ('Angola', 'UGge'): 'Uige',
    ('Argentina', 'CRrdoba'): 'Cordoba',
    ('Argentina', 'Neuqutn'): 'Neuquen',
    ('Argentina', 'RRo Negro'): 'Rio Negro',
    ('Argentina', 'Tucumtn'): 'Tucuman',
    ('Brazil', 'Maranh'): 'Maranhao',
    ('Brazil', 'Par'): 'Para',
    ('Brazil', 'Rondinia'): 'Rondonia',
    ('Cambodia', 'StMng Tr'): 'Stoeng Treng',
    ('Chile', 'BHo-B'): 'Bio-Bio',
    ('Chile', 'La Araucanpa'): 'La Araucania',
    ('Chile', 'Tarapac'): 'Tarapaca',
    ('Chile', 'Tarapace'): 'Tarapaca',
    ('Chile', 'Tarapacm'): 'Tarapaca',
    ('Colombia', 'Caqueti'): 'Caqueta',
    ('Congo (Kinshasa)', 'Cquateur'): 'Equateur',
    ('Congo (Kinshasa)', 'Kasao-Occidental'): 'Kasai-Occidental',
    ('Gabon', 'Moyen-Ogooul'): 'Moyen-Ogooue',
    ('Liberia', 'GrandGedeh'): 'Grand Gedeh',
    ('Liberia', 'GrandKru'): 'Grand Kru',
    ('Morocco', 'LaRyoune - Boujdour - Sakia El Hamra'):
        'Laayoune - Boujdour - Sakia El Hamra',
    ('Morocco', 'Mekncs - Tafilalet'): 'Meknes - Tafilalet',
    ('Norway', 'MOre og Romsdal'): 'More og Romsdal',
    ('Paraguay', 'Alto Paran'): 'Alto Parana',
    ('Paraguay', 'Alto Paranp'): 'Alto Parana',
    ('Paraguay', 'Boqueran'): 'Boqueron',
    ('Paraguay', 'Canindey'): 'Canindeyu',
    ('Paraguay', 'Itapga'): 'Itapua',
    ('Peru', 'HuRnuco'): 'Huanuco',
    ('Peru', 'San Mart'): 'San Martin',
    ('Tunisia', 'Kasssrine'): 'Kasserine',
    ('Tunisia', 'MUdenine'): 'Medenine',
    ('Venezuela', 'Anzoztegui'): 'Anzoategui',
    ('Vietnam', 'Ninh Bmnh'): 'Ninh Binh',
    ('Vietnam', 'ThMi Bmnh'): 'Thai Binh',
    ('Vietnam', 'TrM Vinh'): 'Tra Vinh',
}

# Values the case-anomaly detector flags that are verified correct upstream spellings.
LEGIT_CASE_ANOMALIES = {
    'KwaZulu-Natal',   # South Africa
    'HaMerkaz',        # Israel
    'HaDarom',         # Israel
    'HaZafon',         # Israel
}

# An uppercase letter directly after a lowercase one, or a lowercase letter after two
# uppercase ones at a word start: the signature of an accented letter replaced by a wrong
# ASCII letter. 'Mc' before a capital is exempt (McMurdo, Fort McPherson).
CASE_ANOMALY = re.compile(r'[a-z][A-Z]|\b[A-Z][A-Z][a-z]')
MC_PREFIX = re.compile(r'\bMc(?=[A-Z])')


def fold_ascii(value):
    """NFD, strip combining marks, transliterate the known stroke letters and dashes."""
    decomposed = unicodedata.normalize('NFD', value)
    out = []
    for ch in decomposed:
        if unicodedata.combining(ch):
            continue
        out.append(TRANSLIT.get(ch, ch))
    return ''.join(out)


def fold_ascii_tagged(value):
    """Like fold_ascii, but also returns a per-character flag: True where the output
    character derived from a non-ASCII input character (an accented or transliterated one)."""
    out = []
    tags = []
    for ch in unicodedata.normalize('NFD', value):
        if unicodedata.combining(ch):
            if tags:
                tags[-1] = True
            continue
        replacement = TRANSLIT.get(ch, ch)
        source_nonascii = ord(ch) >= 128
        for c in replacement:
            out.append(c)
            tags.append(source_nonascii)
    return ''.join(out), tags


def damaged_variant_of(alt, folded_name, accent_tags):
    """True when alt is the folded canonical name with exactly one character corrupted at a
    position that came from a non-ASCII character of the display name: the signature of an
    accented letter mangled upstream, not a legitimate alternate transliteration."""
    if len(alt) != len(folded_name) or alt == folded_name:
        return False
    diffs = [i for i, (a, b) in enumerate(zip(folded_name, alt)) if a != b]
    return len(diffs) == 1 and accent_tags[diffs[0]]


def is_ascii(value):
    return all(ord(c) < 128 for c in value)


def collapse_ws(value):
    return ' '.join(value.split())


def looks_corrupted(value):
    """True when an ASCII value carries a mojibake artifact signature."""
    if '?' in value or '\uFFFD' in value:
        return True
    if value in LEGIT_CASE_ANOMALIES:
        return False
    return CASE_ANOMALY.search(MC_PREFIX.sub('', value)) is not None


def clean_field(value, what, ne_id):
    """A single table field: no ';' (field separator), no '|' (list separator), no newline."""
    for banned in (';', '|', '\n', '\r'):
        if banned in value:
            raise SystemExit('Separator %r inside %s of NE_ID %s: %r' % (banned, what, ne_id, value))
    return value


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    in_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else 'naturalearth-populated-places.txt'
    with open(in_path) as f:
        features = json.load(f)['features']

    rows = []
    seen_ids = set()
    counts = {
        'name_corrected': 0, 'name_fallback': 0,
        'alt_repaired': 0, 'alt_dropped_nonascii': 0, 'alt_dropped_damaged': 0,
        'adm1_repaired': 0, 'adm1_omitted_damaged': 0, 'adm1_omitted_nonascii': 0,
    }
    used_repairs = set()
    for feature in features:
        p = feature['properties']
        ne_id = p['NE_ID']
        if ne_id in seen_ids:
            raise SystemExit('Duplicate NE_ID: %s' % ne_id)
        seen_ids.add(ne_id)

        raw_name = collapse_ws((p.get('NAME') or '').strip())
        raw_ascii = collapse_ws((p.get('NAMEASCII') or '').strip())

        # Canonical name: the display name (NAME) folded to ASCII; NAMEASCII where NAME does
        # not reduce to clean ASCII; the explicit correction where both fields are damaged.
        # Hard-fail when no clean canonical name can be derived.
        if ne_id in NAME_CORRECTIONS:
            name = NAME_CORRECTIONS[ne_id]
            counts['name_corrected'] += 1
            used_repairs.add(('NAME', ne_id))
        else:
            name = fold_ascii(raw_name)
            if not name or not is_ascii(name) or looks_corrupted(name):
                fallback = fold_ascii(raw_ascii)
                if not fallback or not is_ascii(fallback) or looks_corrupted(fallback):
                    raise SystemExit('No clean canonical name for NE_ID %s: NAME %r, NAMEASCII %r'
                                     % (ne_id, raw_name, raw_ascii))
                name = fallback
                counts['name_fallback'] += 1
        clean_field(name, 'name', ne_id)

        # Alternates: NAMEASCII where it differs from the canonical name, then NAMEALT
        # (itself pipe separated upstream), NAMEPAR, MEGANAME. Folded to ASCII; a value that
        # stays non-ASCII after the fold is dropped (documented v1 boundary); a damaged value
        # is repaired through ALT_REPAIRS or dropped by damaged_variant_of. Any unrecognized
        # damage hard-fails.
        folded_display, accent_tags = fold_ascii_tagged(raw_name)
        alt_names = []
        alt_seen = {name.lower()}
        # A corrected row's NAMEASCII is damaged by definition; it is never carried.
        if ne_id in NAME_CORRECTIONS:
            counts['alt_dropped_damaged'] += 1
            raw_alts = []
        else:
            raw_alts = [raw_ascii]
        for alt_field in ('NAMEALT', 'NAMEPAR', 'MEGANAME'):
            value = (p.get(alt_field) or '').strip()
            if value:
                raw_alts.extend(part.strip() for part in value.split('|'))
        for raw in raw_alts:
            if not raw:
                continue
            if raw in ALT_REPAIRS:
                folded = ALT_REPAIRS[raw]
                counts['alt_repaired'] += 1
                used_repairs.add(('ALT', raw))
            else:
                folded = collapse_ws(fold_ascii(raw))
            if not is_ascii(folded):
                counts['alt_dropped_nonascii'] += 1
                continue
            if is_ascii(folded_display) and damaged_variant_of(folded, folded_display, accent_tags):
                counts['alt_dropped_damaged'] += 1
                continue
            if looks_corrupted(folded):
                raise SystemExit('Unrecognized damaged alternate name for NE_ID %s: %r'
                                 % (ne_id, raw))
            if folded.lower() in alt_seen:
                continue
            alt_seen.add(folded.lower())
            clean_field(folded, 'altName', ne_id)
            alt_names.append(folded)

        # Location: the geometry point (the authoritative coordinates; the LATITUDE/LONGITUDE
        # attributes are label anchors and can differ slightly).
        lon, lat = feature['geometry']['coordinates'][:2]
        if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
            raise SystemExit('Out of range coordinates for NE_ID %s: %s %s' % (ne_id, lat, lon))

        # Country code: ISO_A2 when it is a real alpha-2 code; the upstream -99 sentinel for
        # territories it assigns no code becomes the empty field (null in the API).
        iso = (p.get('ISO_A2') or '').strip()
        if not (len(iso) == 2 and iso.isalpha() and iso.isupper() and is_ascii(iso)):
            iso = ''

        # Containment: the admin-1 name as the single containment element in v1. Repaired
        # through ADM1_REPAIRS when damaged with a verified spelling; omitted (and counted)
        # when it announces damage with '?' or does not fold to ASCII; hard-fail on an
        # unrecognized anomaly.
        adm0 = (p.get('ADM0NAME') or '').strip()
        adm1 = collapse_ws(fold_ascii((p.get('ADM1NAME') or '').strip()))
        if (adm0, adm1) in ADM1_REPAIRS:
            used_repairs.add(('ADM1', adm0, adm1))
            adm1 = ADM1_REPAIRS[(adm0, adm1)]
            counts['adm1_repaired'] += 1
        elif '?' in adm1 or '\uFFFD' in adm1:
            counts['adm1_omitted_damaged'] += 1
            adm1 = ''
        elif not is_ascii(adm1):
            counts['adm1_omitted_nonascii'] += 1
            adm1 = ''
        elif adm1 and looks_corrupted(adm1):
            raise SystemExit('Unrecognized damaged ADM1NAME for NE_ID %s in %s: %r'
                             % (ne_id, adm0, adm1))
        if adm1:
            clean_field(adm1, 'containment', ne_id)

        # Population: POP_MAX, clamped to 0 for the upstream -99 unknown sentinel.
        pop_max = p.get('POP_MAX')
        population = int(pop_max) if pop_max is not None and pop_max > 0 else 0

        # External identifiers as provided by Natural Earth; never fabricated.
        attributes = []
        wikidata = (p.get('WIKIDATAID') or '').strip()
        if wikidata:
            if not (wikidata.startswith('Q') and wikidata[1:].isdigit()):
                raise SystemExit('Unexpected WIKIDATAID for NE_ID %s: %r' % (ne_id, wikidata))
            attributes.append('wikidata=%s' % wikidata)
        geonames = p.get('GEONAMESID')
        if geonames is not None and float(geonames) > 0:
            if float(geonames) != int(float(geonames)):
                raise SystemExit('Non-integral GEONAMESID for NE_ID %s: %r' % (ne_id, geonames))
            attributes.append('geonames=%d' % int(float(geonames)))
        wof = p.get('WOF_ID')
        if wof is not None and float(wof) > 0:
            if float(wof) != int(float(wof)):
                raise SystemExit('Non-integral WOF_ID for NE_ID %s: %r' % (ne_id, wof))
            attributes.append('whosonfirst=%d' % int(float(wof)))

        row = ';'.join([
            'naturalearth',
            str(ne_id),
            name,
            '|'.join(alt_names),
            '%.5f' % lat,
            '%.5f' % lon,
            iso,
            adm1,
            str(population),
            'CITY',
            '|'.join(attributes),
        ])
        if not is_ascii(row):
            raise SystemExit('Non-ASCII row for NE_ID %s: %r' % (ne_id, row))
        if row.count(';') != 10:
            raise SystemExit('Field-count drift for NE_ID %s: %r' % (ne_id, row))
        rows.append((ne_id, row))

    # A repair table entry that no longer matches anything is stale; fail so a refresh
    # cannot silently carry dead entries.
    expected = ({('NAME', k) for k in NAME_CORRECTIONS}
                | {('ALT', k) for k in ALT_REPAIRS}
                | {('ADM1',) + k for k in ADM1_REPAIRS})
    stale = expected - used_repairs
    if stale:
        raise SystemExit('Stale repair table entries (upstream data changed?): %s' % sorted(stale))

    rows.sort()
    header = HEADER_TEMPLATE.format(
        count=len(rows), date=EXTRACTION_DATE, commit=MIRROR_COMMIT,
        name_fallback=counts['name_fallback'], name_corrected=counts['name_corrected'],
        alt_dropped_damaged=counts['alt_dropped_damaged'],
        alt_dropped_nonascii=counts['alt_dropped_nonascii'],
        alt_repaired=counts['alt_repaired'],
        adm1_repaired=counts['adm1_repaired'],
        adm1_omitted_damaged=counts['adm1_omitted_damaged'],
        adm1_omitted_nonascii=counts['adm1_omitted_nonascii'])
    if not is_ascii(header):
        raise SystemExit('Non-ASCII header')
    with open(out_path, 'w', newline='\n') as out:
        out.write(header)
        for _, row in rows:
            out.write(row)
            out.write('\n')
    print('rows: %d, %s' % (len(rows), ', '.join('%s: %d' % kv for kv in sorted(counts.items()))))


HEADER_TEMPLATE = '''\
# Bundled gazetteer table, authored by the Apache OpenNLP project as a derivation of the
# Natural Earth "Populated Places" dataset (loaded by opennlp.geo.BundledGazetteer).
# {count} rows. Regenerated with dev/derive-populated-places.py in this module; the
# underlying data is in the public domain (see the derivation record below), so this file
# carries the derivation record instead of a source license header. See the Natural Earth
# section of the LICENSE file.
#
# Derivation record:
#   Source     Natural Earth 1:10m Cultural Vectors, Populated Places
#              (ne_10m_populated_places), https://www.naturalearthdata.com/
#   Retrieved  {date}, as geojson/ne_10m_populated_places.geojson from the Natural Earth
#              GitHub distribution https://github.com/nvkelso/natural-earth-vector (master;
#              the file's last upstream change is commit {commit},
#              repository VERSION 5.2.0-pre).
#   License    Natural Earth is in the public domain: "All versions of Natural Earth raster +
#              vector map data found on this website are in the public domain."
#              (https://www.naturalearthdata.com/about/terms-of-use/). See the Natural Earth
#              section of the LICENSE file.
#
# Row format, eleven semicolon separated fields, one record per line:
#
#   source;recordId;name;altNames;lat;lon;iso2;containment;population;featureClass;attributes
#
#   source        Always "naturalearth" in this table (the dataset id, also the provenance
#                 tag of every attribute value in the last field).
#   recordId      The stable Natural Earth id (upstream field NE_ID).
#   name          The canonical place name: the upstream display name (NAME) folded to
#                 ASCII; the upstream transliteration (NAMEASCII) for the {name_fallback}
#                 rows whose NAME does not reduce to clean ASCII; explicitly corrected for
#                 {name_corrected} row damaged in both fields (see the mojibake note).
#   altNames      Pipe separated alternate names: NAMEASCII where it differs from the
#                 derived name, then the upstream fields NAMEALT, NAMEPAR, MEGANAME
#                 (pipe-split as published), deduplicated case-insensitively; possibly
#                 empty.
#   lat, lon      WGS84 decimal degrees from the feature's point geometry, 5 decimals.
#   iso2          ISO 3166-1 alpha-2 country code (upstream field ISO_A2); empty where the
#                 source assigns no code (its -99 sentinel, e.g. disputed territories).
#   containment   Pipe separated admin containment chain, outermost first. In v1 a single
#                 element, the admin-1 name (upstream field ADM1NAME), possibly empty.
#   population    Peak population estimate (upstream field POP_MAX); 0 when unknown
#                 (its -99 sentinel).
#   featureClass  Always CITY: every record in this dataset is a populated place.
#   attributes    Pipe separated key=value pairs of stable external identifiers as provided
#                 by Natural Earth, possibly empty: wikidata (WIKIDATAID), geonames
#                 (GEONAMESID where positive), whosonfirst (WOF_ID). Keys the source does not
#                 provide are never fabricated.
#
# ASCII note: this file is deliberately pure ASCII. Names and admin-1 values are
# accent-stripped to ASCII (NFD, drop combining marks, standard stroke-letter
# transliterations); an alternate name or containment element that is not ASCII after the
# fold is dropped ({alt_dropped_nonascii} alternate names, {adm1_omitted_nonascii}
# containment elements), and a display name that is not ASCII after the fold falls back to
# the upstream transliteration. Matching still works for accented queries because
# BundledGazetteer folds queries and index keys through the same chain; native-script names
# belong to a later, richer data tier.
#
# Mojibake note: the upstream distribution itself carries encoding damage in some fields
# (an accented letter replaced by '?' or by a wrong ASCII letter). This derivation never
# carries a value it knows to be damaged:
#   - a containment value that announces damage with '?' is omitted ({adm1_omitted_damaged}
#     elements);
#   - a value verified against its accent-bearing upstream sibling or the documented place
#     name is repaired to that spelling ({adm1_repaired} containment elements,
#     {alt_repaired} alternate names, {name_corrected} canonical name); the audited repair
#     tables are in the generation script;
#   - an alternate name that is a one-character corruption of the derived name at an
#     accented position is dropped ({alt_dropped_damaged} values, upstream forms like
#     'Zarich' for Zurich).
# The generation script hard-fails on anomalies it does not recognize, so a refresh forces
# a reviewed decision instead of importing new corruption.
#
# A line starting with '#' is a comment; data rows carry no inline comments.
'''


if __name__ == '__main__':
    sys.exit(main())
