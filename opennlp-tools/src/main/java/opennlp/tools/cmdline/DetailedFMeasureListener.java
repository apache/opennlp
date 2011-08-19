package opennlp.tools.cmdline;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import opennlp.tools.util.Span;
import opennlp.tools.util.eval.EvaluationSampleListener;

/**
 * This listener will gather detailed information about the sample under evaluation and will
 * allow detailed FMeasure for each outcome.
 */
public abstract class DetailedFMeasureListener<T> implements
    EvaluationSampleListener<T> {

  private int samples = 0;
  private Stats generalStats = new Stats();
  private Map<String, Stats> statsForOutcome = new HashMap<String, Stats>();

  protected abstract Span[] asSpanArray(T sample);

  public void correctlyClassified(T reference, T prediction) {
    samples++;
    // add all true positives!
    Span[] spans = asSpanArray(reference);
    for (Span span : spans) {
      addTruePositive(span.getType());
    }
  }

  public void missclassified(T reference, T prediction) {
    samples++;
    Span[] references = asSpanArray(reference);
    Span[] predictions = asSpanArray(prediction);

    Set<Span> refSet = new HashSet<Span>(Arrays.asList(references));
    Set<Span> predSet = new HashSet<Span>(Arrays.asList(predictions));

    for (Span ref : refSet) {
      if (predSet.contains(ref)) {
        addTruePositive(ref.getType());
      } else {
        addFalseNegative(ref.getType());
      }
    }

    for (Span pred : predSet) {
      if (!refSet.contains(pred)) {
        addFalsePositive(pred.getType());
      }
    }
  }

  private void addTruePositive(String type) {
    Stats s = initStatsForOutcomeAndGet(type);
    s.incrementTruePositive();
    s.incrementTarget();

    generalStats.incrementTruePositive();
    generalStats.incrementTarget();
  }

  private void addFalsePositive(String type) {
    Stats s = initStatsForOutcomeAndGet(type);
    s.incrementFalsePositive();
    generalStats.incrementFalsePositive();
  }

  private void addFalseNegative(String type) {
    Stats s = initStatsForOutcomeAndGet(type);
    s.incrementTarget();
    generalStats.incrementTarget();

  }

  private Stats initStatsForOutcomeAndGet(String type) {
    if (!statsForOutcome.containsKey(type)) {
      statsForOutcome.put(type, new Stats());
    }
    return statsForOutcome.get(type);
  }

  private static final String PERCENT = "%\u00207.2f%%";
  private static final String FORMAT = "%8s: precision: " + PERCENT
      + ";  recall: " + PERCENT + "; F1: " + PERCENT + ".";
  private static final String FORMAT_EXTRA = FORMAT
      + " [target: %3d; tp: %3d; fp: %3d]";

  @Override
  public String toString() {
    StringBuilder ret = new StringBuilder();
    int tp = generalStats.getTruePositives();
    int found = generalStats.getFalsePositives() + tp;
    ret.append("processed " + samples + " samples with "
        + generalStats.getTarget() + " entities; found: " + found
        + " entities; correct: " + tp + ".\n");

    ret.append(String.format(FORMAT, "TOTAL",
        zeroOrPositive(generalStats.getPrecisionScore() * 100),
        zeroOrPositive(generalStats.getRecallScore() * 100),
        zeroOrPositive(generalStats.getFMeasure() * 100)));
    ret.append("\n");
    SortedSet<String> set = new TreeSet<String>(new F1Comparator());
    set.addAll(statsForOutcome.keySet());
    for (String type : set) {

      ret.append(String.format(FORMAT_EXTRA, type,
          zeroOrPositive(statsForOutcome.get(type).getPrecisionScore() * 100),
          zeroOrPositive(statsForOutcome.get(type).getRecallScore() * 100),
          zeroOrPositive(statsForOutcome.get(type).getFMeasure() * 100),
          statsForOutcome.get(type).getTarget(), statsForOutcome.get(type)
              .getTruePositives(), statsForOutcome.get(type)
              .getFalsePositives()));
      ret.append("\n");
    }

    return ret.toString();

  }

  private double zeroOrPositive(double v) {
    if (v < 0) {
      return 0;
    }
    return v;
  }

  private class F1Comparator implements Comparator<String> {
    public int compare(String o1, String o2) {
      if (o1.equals(o2))
        return 0;
      double t1 = 0;
      double t2 = 0;

      if (statsForOutcome.containsKey(o1))
        t1 += statsForOutcome.get(o1).getFMeasure();
      if (statsForOutcome.containsKey(o2))
        t2 += statsForOutcome.get(o2).getFMeasure();

      t1 = zeroOrPositive(t1);
      t2 = zeroOrPositive(t2);

      if (t1 + t2 > 0d) {
        if (t1 > t2)
          return -1;
        return 1;
      }
      return o1.compareTo(o2);
    }

  }

  /**
   * Store the statistics.
   */
  private class Stats {

    // maybe we could use FMeasure class, but it wouldn't allow us to get
    // details like total number of false positives and true positives.

    private int falsePositiveCounter = 0;
    private int truePositiveCounter = 0;
    private int targetCounter = 0;

    public void incrementFalsePositive() {
      falsePositiveCounter++;
    }

    public void incrementTruePositive() {
      truePositiveCounter++;
    }

    public void incrementTarget() {
      targetCounter++;
    }

    public int getFalsePositives() {
      return falsePositiveCounter;
    }

    public int getTruePositives() {
      return truePositiveCounter;
    }

    public int getTarget() {
      return targetCounter;
    }

    /**
     * Retrieves the arithmetic mean of the precision scores calculated for each
     * evaluated sample.
     * 
     * @return the arithmetic mean of all precision scores
     */
    public double getPrecisionScore() {
      int tp = getTruePositives();
      int selected = tp + getFalsePositives();
      return selected > 0 ? (double) tp / (double) selected : 0;
    }

    /**
     * Retrieves the arithmetic mean of the recall score calculated for each
     * evaluated sample.
     * 
     * @return the arithmetic mean of all recall scores
     */
    public double getRecallScore() {
      int target = getTarget();
      int tp = getTruePositives();
      return target > 0 ? (double) tp / (double) target : 0;
    }

    /**
     * Retrieves the f-measure score.
     * 
     * f-measure = 2 * precision * recall / (precision + recall)
     * 
     * @return the f-measure or -1 if precision + recall <= 0
     */
    public double getFMeasure() {

      if (getPrecisionScore() + getRecallScore() > 0) {
        return 2 * (getPrecisionScore() * getRecallScore())
            / (getPrecisionScore() + getRecallScore());
      } else {
        // cannot divide by zero, return error code
        return -1;
      }
    }

  }

}
