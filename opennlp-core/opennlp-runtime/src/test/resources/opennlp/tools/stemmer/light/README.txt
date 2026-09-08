Parity fixtures for the light and minimal stemmers.

The reference revision is Apache Lucene commit
4965e8d4d960445a0522fae512c60c6d8f11fc29. Each stemmer Javadoc links to its
source class at that revision.

The paired Lucene test data supplies de-light, de-minimal, es-light,
es-minimal, fi-light, fr-light, fr-minimal, hu-light, it-light, pt-light,
ru-light, and sv-light. The files come from the matching language directory
under lucene/analysis/common/src/test. English minimal uses words from
en/porterTestData.zip. Swedish minimal uses words from sv/svlighttestdata.zip.
Their expected stems were produced by EnglishMinimalStemmer and
SwedishMinimalStemmer. The four Norwegian files copy the complete nb_light,
nn_light, nb_minimal, and nn_minimal lists without comment lines.

Except for the complete Norwegian lists, a fixture contains the first 1500
sorted pairs whose stem differs from the word and the first 500 sorted pairs
whose stem is unchanged. Each row is word<TAB>expected-stem.

The CLEF-derived data is covered by the BSD notice in the stemmer sources and
the distribution LICENSE. The English fixture derives from the Porter
vocabulary covered by the Snowball BSD notice already in LICENSE. The Spanish
fixture uses Apache Lucene's Spanish plural reference pairs.
