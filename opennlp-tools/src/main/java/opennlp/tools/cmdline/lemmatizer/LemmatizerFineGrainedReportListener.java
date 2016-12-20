/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.cmdline.lemmatizer;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmatizerEvaluationMonitor;
import opennlp.tools.util.Span;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.eval.Mean;

/**
 * Generates a detailed report for the Lemmatizer.
 * <p>
 * It is possible to use it from an API and access the statistics using the
 * provided getters.
 *
 */
public class LemmatizerFineGrainedReportListener
    implements LemmatizerEvaluationMonitor {

  private final PrintStream printStream;
  private final Stats stats = new Stats();

  private static final char[] alpha = { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
      'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
      'x', 'y', 'z' };

  /**
   * Creates a listener that will print to {@link System#err}
   */
  public LemmatizerFineGrainedReportListener() {
    this(System.err);
  }

  /**
   * Creates a listener that prints to a given {@link OutputStream}
   */
  public LemmatizerFineGrainedReportListener(OutputStream outputStream) {
    this.printStream = new PrintStream(outputStream);
  }

  // methods inherited from EvaluationMonitor

  public void missclassified(LemmaSample reference, LemmaSample prediction) {
    stats.add(reference, prediction);
  }

  public void correctlyClassified(LemmaSample reference,
      LemmaSample prediction) {
    stats.add(reference, prediction);
  }

  /**
   * Writes the report to the {@link OutputStream}. Should be called only after
   * the evaluation process
   */
  public void writeReport() {
    printGeneralStatistics();
    // token stats
    printTokenErrorRank();
    printTokenOcurrenciesRank();
    // tag stats
    printTagsErrorRank();
    // confusion tables
    printGeneralConfusionTable();
    printDetailedConfusionMatrix();
  }

  // api methods
  // general stats

  public long getNumberOfSentences() {
    return stats.getNumberOfSentences();
  }

  public double getAverageSentenceSize() {
    return stats.getAverageSentenceSize();
  }

  public int getMinSentenceSize() {
    return stats.getMinSentenceSize();
  }

  public int getMaxSentenceSize() {
    return stats.getMaxSentenceSize();
  }

  public int getNumberOfTags() {
    return stats.getNumberOfTags();
  }

  public double getAccuracy() {
    return stats.getAccuracy();
  }

  // token stats

  public double getTokenAccuracy(String token) {
    return stats.getTokenAccuracy(token);
  }

  public SortedSet<String> getTokensOrderedByFrequency() {
    return stats.getTokensOrderedByFrequency();
  }

  public int getTokenFrequency(String token) {
    return stats.getTokenFrequency(token);
  }

  public int getTokenErrors(String token) {
    return stats.getTokenErrors(token);
  }

  public SortedSet<String> getTokensOrderedByNumberOfErrors() {
    return stats.getTokensOrderedByNumberOfErrors();
  }

  public SortedSet<String> getTagsOrderedByErrors() {
    return stats.getTagsOrderedByErrors();
  }

  public int getTagFrequency(String tag) {
    return stats.getTagFrequency(tag);
  }

  public int getTagErrors(String tag) {
    return stats.getTagErrors(tag);
  }

  public double getTagPrecision(String tag) {
    return stats.getTagPrecision(tag);
  }

  public double getTagRecall(String tag) {
    return stats.getTagRecall(tag);
  }

  public double getTagFMeasure(String tag) {
    return stats.getTagFMeasure(tag);
  }

  public SortedSet<String> getConfusionMatrixTagset() {
    return stats.getConfusionMatrixTagset();
  }

  public SortedSet<String> getConfusionMatrixTagset(String token) {
    return stats.getConfusionMatrixTagset(token);
  }

  public double[][] getConfusionMatrix() {
    return stats.getConfusionMatrix();
  }

  public double[][] getConfusionMatrix(String token) {
    return stats.getConfusionMatrix(token);
  }

  private String matrixToString(SortedSet<String> tagset, double[][] data,
      boolean filter) {
    // we dont want to print trivial cases (acc=1)
    int initialIndex = 0;
    String[] tags = tagset.toArray(new String[tagset.size()]);
    StringBuilder sb = new StringBuilder();
    int minColumnSize = Integer.MIN_VALUE;
    String[][] matrix = new String[data.length][data[0].length];
    for (int i = 0; i < data.length; i++) {
      int j = 0;
      for (; j < data[i].length - 1; j++) {
        matrix[i][j] = data[i][j] > 0 ? Integer.toString((int) data[i][j])
            : ".";
        if (minColumnSize < matrix[i][j].length()) {
          minColumnSize = matrix[i][j].length();
        }
      }
      matrix[i][j] = MessageFormat.format("{0,number,#.##%}", data[i][j]);
      if (data[i][j] == 1 && filter) {
        initialIndex = i + 1;
      }
    }

    final String headerFormat = "%" + (minColumnSize + 2) + "s "; // | 1234567 |
    final String cellFormat = "%" + (minColumnSize + 2) + "s "; // | 12345 |
    final String diagFormat = " %" + (minColumnSize + 2) + "s";
    for (int i = initialIndex; i < tagset.size(); i++) {
      sb.append(String.format(headerFormat,
          generateAlphaLabel(i - initialIndex).trim()));
    }
    sb.append("| Accuracy | <-- classified as\n");
    for (int i = initialIndex; i < data.length; i++) {
      int j = initialIndex;
      for (; j < data[i].length - 1; j++) {
        if (i == j) {
          String val = "<" + matrix[i][j] + ">";
          sb.append(String.format(diagFormat, val));
        } else {
          sb.append(String.format(cellFormat, matrix[i][j]));
        }
      }
      sb.append(String.format("|   %-6s |   %3s = ", matrix[i][j],
          generateAlphaLabel(i - initialIndex))).append(tags[i]);
      sb.append("\n");
    }
    return sb.toString();
  }

  private void printGeneralStatistics() {
    printHeader("Evaluation summary");
    printStream.append(String.format("%21s: %6s", "Number of sentences",
        Long.toString(getNumberOfSentences()))).append("\n");
    printStream.append(
        String.format("%21s: %6s", "Min sentence size", getMinSentenceSize()))
        .append("\n");
    printStream.append(
        String.format("%21s: %6s", "Max sentence size", getMaxSentenceSize()))
        .append("\n");
    printStream
        .append(String.format("%21s: %6s", "Average sentence size",
            MessageFormat.format("{0,number,#.##}", getAverageSentenceSize())))
        .append("\n");
    printStream
        .append(String.format("%21s: %6s", "Tags count", getNumberOfTags()))
        .append("\n");
    printStream
        .append(String.format("%21s: %6s", "Accuracy",
            MessageFormat.format("{0,number,#.##%}", getAccuracy())))
        .append("\n");
    printFooter("Evaluation Corpus Statistics");
  }

  private void printTokenOcurrenciesRank() {
    printHeader("Most frequent tokens");

    SortedSet<String> toks = getTokensOrderedByFrequency();
    final int maxLines = 20;

    int maxTokSize = 5;

    int count = 0;
    Iterator<String> tokIterator = toks.iterator();
    while (tokIterator.hasNext() && count++ < maxLines) {
      String tok = tokIterator.next();
      if (tok.length() > maxTokSize) {
        maxTokSize = tok.length();
      }
    }

    int tableSize = maxTokSize + 19;
    String format = "| %3s | %6s | %" + maxTokSize + "s |";

    printLine(tableSize);
    printStream.append(String.format(format, "Pos", "Count", "Token"))
        .append("\n");
    printLine(tableSize);

    // get the first 20 errors
    count = 0;
    tokIterator = toks.iterator();
    while (tokIterator.hasNext() && count++ < maxLines) {
      String tok = tokIterator.next();
      int ocurrencies = getTokenFrequency(tok);

      printStream.append(String.format(format, count, ocurrencies, tok)

      ).append("\n");
    }
    printLine(tableSize);
    printFooter("Most frequent tokens");
  }

  private void printTokenErrorRank() {
    printHeader("Tokens with the highest number of errors");
    printStream.append("\n");

    SortedSet<String> toks = getTokensOrderedByNumberOfErrors();
    int maxTokenSize = 5;

    int count = 0;
    Iterator<String> tokIterator = toks.iterator();
    while (tokIterator.hasNext() && count++ < 20) {
      String tok = tokIterator.next();
      if (tok.length() > maxTokenSize) {
        maxTokenSize = tok.length();
      }
    }

    int tableSize = 31 + maxTokenSize;

    String format = "| %" + maxTokenSize + "s | %6s | %5s | %7s |\n";

    printLine(tableSize);
    printStream
        .append(String.format(format, "Token", "Errors", "Count", "% Err"));
    printLine(tableSize);

    // get the first 20 errors
    count = 0;
    tokIterator = toks.iterator();
    while (tokIterator.hasNext() && count++ < 20) {
      String tok = tokIterator.next();
      int ocurrencies = getTokenFrequency(tok);
      int errors = getTokenErrors(tok);
      String rate = MessageFormat.format("{0,number,#.##%}",
          (double) errors / ocurrencies);

      printStream.append(String.format(format, tok, errors, ocurrencies, rate)

      );
    }
    printLine(tableSize);
    printFooter("Tokens with the highest number of errors");
  }

  private void printTagsErrorRank() {
    printHeader("Detailed Accuracy By Tag");
    SortedSet<String> tags = getTagsOrderedByErrors();
    printStream.append("\n");

    int maxTagSize = 3;

    for (String t : tags) {
      if (t.length() > maxTagSize) {
        maxTagSize = t.length();
      }
    }

    int tableSize = 65 + maxTagSize;

    String headerFormat = "| %" + maxTagSize
        + "s | %6s | %6s | %7s | %9s | %6s | %9s |\n";
    String format = "| %" + maxTagSize
        + "s | %6s | %6s | %-7s | %-9s | %-6s | %-9s |\n";

    printLine(tableSize);
    printStream.append(String.format(headerFormat, "Tag", "Errors", "Count",
        "% Err", "Precision", "Recall", "F-Measure"));
    printLine(tableSize);

    for (String tag : tags) {
      int ocurrencies = getTagFrequency(tag);
      int errors = getTagErrors(tag);
      String rate = MessageFormat.format("{0,number,#.###}",
              (double) errors / ocurrencies);

      double p = getTagPrecision(tag);
      double r = getTagRecall(tag);
      double f = getTagFMeasure(tag);

      printStream.append(String.format(format, tag, errors, ocurrencies, rate,
              MessageFormat.format("{0,number,#.###}", p > 0 ? p : 0),
              MessageFormat.format("{0,number,#.###}", r > 0 ? r : 0),
              MessageFormat.format("{0,number,#.###}", f > 0 ? f : 0))

      );
    }
    printLine(tableSize);

    printFooter("Tags with the highest number of errors");
  }

  private void printGeneralConfusionTable() {
    printHeader("Confusion matrix");

    SortedSet<String> labels = getConfusionMatrixTagset();

    double[][] confusionMatrix = getConfusionMatrix();

    printStream.append("\nTags with 100% accuracy: ");
    int line = 0;
    for (String label : labels) {
      if (confusionMatrix[line][confusionMatrix[0].length - 1] == 1) {
        printStream.append(label).append(" (")
            .append(Integer.toString((int) confusionMatrix[line][line]))
            .append(") ");
      }
      line++;
    }

    printStream.append("\n\n");

    printStream.append(matrixToString(labels, confusionMatrix, true));

    printFooter("Confusion matrix");
  }

  private void printDetailedConfusionMatrix() {
    printHeader("Confusion matrix for tokens");
    printStream.append("  sorted by number of errors\n");
    SortedSet<String> toks = getTokensOrderedByNumberOfErrors();

    for (String t : toks) {
      double acc = getTokenAccuracy(t);
      if (acc < 1) {
        printStream.append("\n[")
            .append(t).append("]\n").append(String.format("%12s: %-8s",
                "Accuracy", MessageFormat.format("{0,number,#.##%}", acc)))
            .append("\n");
        printStream.append(String.format("%12s: %-8s", "Ocurrencies",
            Integer.toString(getTokenFrequency(t)))).append("\n");
        printStream.append(String.format("%12s: %-8s", "Errors",
            Integer.toString(getTokenErrors(t)))).append("\n");

        SortedSet<String> labels = getConfusionMatrixTagset(t);

        double[][] confusionMatrix = getConfusionMatrix(t);

        printStream.append(matrixToString(labels, confusionMatrix, false));
      }
    }
    printFooter("Confusion matrix for tokens");
  }

  /** Auxiliary method that prints a emphasised report header */
  private void printHeader(String text) {
    printStream.append("=== ").append(text).append(" ===\n");
  }

  /** Auxiliary method that prints a marker to the end of a report */
  private void printFooter(String text) {
    printStream.append("\n<-end> ").append(text).append("\n\n");
  }

  /** Auxiliary method that prints a horizontal line of a given size */
  private void printLine(int size) {
    for (int i = 0; i < size; i++) {
      printStream.append("-");
    }
    printStream.append("\n");
  }

  private static String generateAlphaLabel(int index) {

    char labelChars[] = new char[3];
    int i;

    for (i = 2; i >= 0; i--) {
      labelChars[i] = alpha[index % alpha.length];
      index = index / alpha.length - 1;
      if (index < 0) {
        break;
      }
    }

    return new String(labelChars);
  }

  private class Stats {

    // general statistics
    private final Mean accuracy = new Mean();
    private final Mean averageSentenceLength = new Mean();
    private int minimalSentenceLength = Integer.MAX_VALUE;
    private int maximumSentenceLength = Integer.MIN_VALUE;

    // token statistics
    private final Map<String, Mean> tokAccuracies = new HashMap<>();
    private final Map<String, Counter> tokOcurrencies = new HashMap<>();
    private final Map<String, Counter> tokErrors = new HashMap<>();

    // tag statistics
    private final Map<String, Counter> tagOcurrencies = new HashMap<>();
    private final Map<String, Counter> tagErrors = new HashMap<>();
    private final Map<String, FMeasure> tagFMeasure = new HashMap<>();

    // represents a Confusion Matrix that aggregates all tokens
    private final Map<String, ConfusionMatrixLine> generalConfusionMatrix = new HashMap<>();

    // represents a set of Confusion Matrix for each token
    private final Map<String, Map<String, ConfusionMatrixLine>> tokenConfusionMatrix = new HashMap<>();

    public void add(LemmaSample reference, LemmaSample prediction) {
      int length = reference.getTokens().length;
      averageSentenceLength.add(length);

      if (minimalSentenceLength > length) {
        minimalSentenceLength = length;
      }
      if (maximumSentenceLength < length) {
        maximumSentenceLength = length;
      }

      String[] toks = reference.getTokens();
      String[] refs = reference.getTags();
      String[] preds = prediction.getTags();

      updateTagFMeasure(refs, preds);

      for (int i = 0; i < toks.length; i++) {
        add(toks[i], refs[i], preds[i]);
      }
    }

    /**
     * Includes a new evaluation data
     *
     * @param tok
     *          the evaluated token
     * @param ref
     *          the reference pos tag
     * @param pred
     *          the predicted pos tag
     */
    private void add(String tok, String ref, String pred) {
      // token stats
      if (!tokAccuracies.containsKey(tok)) {
        tokAccuracies.put(tok, new Mean());
        tokOcurrencies.put(tok, new Counter());
        tokErrors.put(tok, new Counter());
      }
      tokOcurrencies.get(tok).increment();

      // tag stats
      if (!tagOcurrencies.containsKey(ref)) {
        tagOcurrencies.put(ref, new Counter());
        tagErrors.put(ref, new Counter());
      }
      tagOcurrencies.get(ref).increment();

      // updates general, token and tag error stats
      if (ref.equals(pred)) {
        tokAccuracies.get(tok).add(1);
        accuracy.add(1);
      } else {
        tokAccuracies.get(tok).add(0);
        tokErrors.get(tok).increment();
        tagErrors.get(ref).increment();
        accuracy.add(0);
      }

      // populate confusion matrixes
      if (!generalConfusionMatrix.containsKey(ref)) {
        generalConfusionMatrix.put(ref, new ConfusionMatrixLine(ref));
      }
      generalConfusionMatrix.get(ref).increment(pred);

      if (!tokenConfusionMatrix.containsKey(tok)) {
        tokenConfusionMatrix.put(tok,
            new HashMap<String, ConfusionMatrixLine>());
      }
      if (!tokenConfusionMatrix.get(tok).containsKey(ref)) {
        tokenConfusionMatrix.get(tok).put(ref, new ConfusionMatrixLine(ref));
      }
      tokenConfusionMatrix.get(tok).get(ref).increment(pred);
    }

    private void updateTagFMeasure(String[] refs, String[] preds) {
      // create a set with all tags
      Set<String> tags = new HashSet<>(Arrays.asList(refs));
      tags.addAll(Arrays.asList(preds));

      // create samples for each tag
      for (String tag : tags) {
        List<Span> reference = new ArrayList<>();
        List<Span> prediction = new ArrayList<>();
        for (int i = 0; i < refs.length; i++) {
          if (refs[i].equals(tag)) {
            reference.add(new Span(i, i + 1));
          }
          if (preds[i].equals(tag)) {
            prediction.add(new Span(i, i + 1));
          }
        }
        if (!this.tagFMeasure.containsKey(tag)) {
          this.tagFMeasure.put(tag, new FMeasure());
        }
        // populate the fmeasure
        this.tagFMeasure.get(tag).updateScores(
            reference.toArray(new Span[reference.size()]),
            prediction.toArray(new Span[prediction.size()]));
      }
    }

    public double getAccuracy() {
      return accuracy.mean();
    }

    public int getNumberOfTags() {
      return this.tagOcurrencies.keySet().size();
    }

    public long getNumberOfSentences() {
      return this.averageSentenceLength.count();
    }

    public double getAverageSentenceSize() {
      return this.averageSentenceLength.mean();
    }

    public int getMinSentenceSize() {
      return this.minimalSentenceLength;
    }

    public int getMaxSentenceSize() {
      return this.maximumSentenceLength;
    }

    public double getTokenAccuracy(String token) {
      return tokAccuracies.get(token).mean();
    }

    public int getTokenErrors(String token) {
      return tokErrors.get(token).value();
    }

    public int getTokenFrequency(String token) {
      return tokOcurrencies.get(token).value();
    }

    public SortedSet<String> getTokensOrderedByFrequency() {
      SortedSet<String> toks = new TreeSet<>(new Comparator<String>() {
        public int compare(String o1, String o2) {
          if (o1.equals(o2)) {
            return 0;
          }
          int e1 = 0, e2 = 0;
          if (tokOcurrencies.containsKey(o1))
            e1 = tokOcurrencies.get(o1).value();
          if (tokOcurrencies.containsKey(o2))
            e2 = tokOcurrencies.get(o2).value();
          if (e1 == e2) {
            return o1.compareTo(o2);
          }
          return e2 - e1;
        }
      });

      toks.addAll(tokOcurrencies.keySet());

      return Collections.unmodifiableSortedSet(toks);
    }

    public SortedSet<String> getTokensOrderedByNumberOfErrors() {
      SortedSet<String> toks = new TreeSet<>(new Comparator<String>() {
        public int compare(String o1, String o2) {
          if (o1.equals(o2)) {
            return 0;
          }
          int e1 = 0, e2 = 0;
          if (tokErrors.containsKey(o1))
            e1 = tokErrors.get(o1).value();
          if (tokErrors.containsKey(o2))
            e2 = tokErrors.get(o2).value();
          if (e1 == e2) {
            return o1.compareTo(o2);
          }
          return e2 - e1;
        }
      });
      toks.addAll(tokErrors.keySet());
      return toks;
    }

    public int getTagFrequency(String tag) {
      return tagOcurrencies.get(tag).value();
    }

    public int getTagErrors(String tag) {
      return tagErrors.get(tag).value();
    }

    public double getTagFMeasure(String tag) {
      return tagFMeasure.get(tag).getFMeasure();
    }

    public double getTagRecall(String tag) {
      return tagFMeasure.get(tag).getRecallScore();
    }

    public double getTagPrecision(String tag) {
      return tagFMeasure.get(tag).getPrecisionScore();
    }

    public SortedSet<String> getTagsOrderedByErrors() {
      SortedSet<String> tags = new TreeSet<>(new Comparator<String>() {
        public int compare(String o1, String o2) {
          if (o1.equals(o2)) {
            return 0;
          }
          int e1 = 0, e2 = 0;
          if (tagErrors.containsKey(o1))
            e1 = tagErrors.get(o1).value();
          if (tagErrors.containsKey(o2))
            e2 = tagErrors.get(o2).value();
          if (e1 == e2) {
            return o1.compareTo(o2);
          }
          return e2 - e1;
        }
      });
      tags.addAll(tagErrors.keySet());
      return Collections.unmodifiableSortedSet(tags);
    }

    public SortedSet<String> getConfusionMatrixTagset() {
      return getConfusionMatrixTagset(generalConfusionMatrix);
    }

    public double[][] getConfusionMatrix() {
      return createConfusionMatrix(getConfusionMatrixTagset(),
          generalConfusionMatrix);
    }

    public SortedSet<String> getConfusionMatrixTagset(String token) {
      return getConfusionMatrixTagset(tokenConfusionMatrix.get(token));
    }

    public double[][] getConfusionMatrix(String token) {
      return createConfusionMatrix(getConfusionMatrixTagset(token),
          tokenConfusionMatrix.get(token));
    }

    /**
     * Creates a matrix with N lines and N + 1 columns with the data from
     * confusion matrix. The last column is the accuracy.
     */
    private double[][] createConfusionMatrix(SortedSet<String> tagset,
        Map<String, ConfusionMatrixLine> data) {
      int size = tagset.size();
      double[][] matrix = new double[size][size + 1];
      int line = 0;
      for (String ref : tagset) {
        int column = 0;
        for (String pred : tagset) {
          matrix[line][column] = data.get(ref) != null
              ? data.get(ref).getValue(pred) : 0;
          column++;
        }
        // set accuracy
        matrix[line][column] = data.get(ref) != null
            ? data.get(ref).getAccuracy() : 0;
        line++;
      }

      return matrix;
    }

    private SortedSet<String> getConfusionMatrixTagset(
        Map<String, ConfusionMatrixLine> data) {
      SortedSet<String> tags = new TreeSet<>(
          new CategoryComparator(data));
      tags.addAll(data.keySet());
      List<String> col = new LinkedList<>();
      for (String t : tags) {
        col.addAll(data.get(t).line.keySet());
      }
      tags.addAll(col);
      return Collections.unmodifiableSortedSet(tags);
    }
  }

  /**
   * A comparator that sorts the confusion matrix labels according to the
   * accuracy of each line
   */
  private static class CategoryComparator implements Comparator<String> {

    private Map<String, ConfusionMatrixLine> confusionMatrix;

    public CategoryComparator(
        Map<String, ConfusionMatrixLine> confusionMatrix) {
      this.confusionMatrix = confusionMatrix;
    }

    public int compare(String o1, String o2) {
      if (o1.equals(o2)) {
        return 0;
      }
      ConfusionMatrixLine t1 = confusionMatrix.get(o1);
      ConfusionMatrixLine t2 = confusionMatrix.get(o2);
      if (t1 == null || t2 == null) {
        if (t1 == null) {
          return 1;
        } else if (t2 == null) {
          return -1;
        }
        return 0;
      }
      double r1 = t1.getAccuracy();
      double r2 = t2.getAccuracy();
      if (r1 == r2) {
        return o1.compareTo(o2);
      }
      if (r2 > r1) {
        return 1;
      }
      return -1;
    }

  }

  /**
   * Represents a line in the confusion table.
   */
  private static class ConfusionMatrixLine {

    private Map<String, Counter> line = new HashMap<>();
    private String ref;
    private int total = 0;
    private int correct = 0;
    private double acc = -1;

    /**
     * Creates a new {@link ConfusionMatrixLine}
     *
     * @param ref
     *          the reference column
     */
    public ConfusionMatrixLine(String ref) {
      this.ref = ref;
    }

    /**
     * Increments the counter for the given column and updates the statistics.
     *
     * @param column
     *          the column to be incremented
     */
    public void increment(String column) {
      total++;
      if (column.equals(ref))
        correct++;
      if (!line.containsKey(column)) {
        line.put(column, new Counter());
      }
      line.get(column).increment();
    }

    /**
     * Gets the calculated accuracy of this element
     *
     * @return the accuracy
     */
    public double getAccuracy() {
      // we save the accuracy because it is frequently used by the comparator
      if (acc == -1) {
        if (total == 0)
          acc = 0;
        acc = (double) correct / (double) total;
      }
      return acc;
    }

    /**
     * Gets the value given a column
     *
     * @param column
     *          the column
     * @return the counter value
     */
    public int getValue(String column) {
      Counter c = line.get(column);
      if (c == null)
        return 0;
      return c.value();
    }
  }

  /**
   * Implements a simple counter
   */
  private static class Counter {
    private int c = 0;

    public void increment() {
      c++;
    }

    public int value() {
      return c;
    }
  }

}
