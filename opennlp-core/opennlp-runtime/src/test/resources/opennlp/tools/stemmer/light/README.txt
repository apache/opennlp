Parity fixtures for the light and minimal stemmers.

Each <name>.tsv holds word<TAB>expected-stem pairs produced by the original Apache Lucene
analysis-common stemmers (the code these classes are adapted from). Each file samples up to 1500
pairs where the stem differs from the word plus 500 where it does not; the Norwegian files are
complete copies of Lucene's small vocabulary lists, split by written standard.

The vocabulary word lists trace back to Jacques Savoy's CLEF resources
(http://members.unine.ch/jacques.savoy/clef/index.html), BSD-licensed; see the notice carried in
the stemmer sources and the distribution LICENSE.

To regenerate: compile the Lucene stemmer classes standalone together with a small reflection
driver that reads a word list and writes word<TAB>stem lines, run it per algorithm (Norwegian
with flags 1 for Bokmaal and 2 for Nynorsk), and re-sample. Any behavior difference against the
originals then shows up as a parity test failure.
