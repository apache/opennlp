package opennlp.dl.doccat.scoring;

import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class AverageClassificationScoringStrategyTest {

  @Test
  public void calculateAverage1() {

    final List<double[]> scores = new LinkedList<>();
    scores.add(new double[]{1, 2, 3, 4, 5});
    scores.add(new double[]{1, 2, 3, 4, 5});
    scores.add(new double[]{1, 2, 3, 4, 5});

    final ClassificationScoringStrategy strategy = new AverageClassifcationScoringStrategy();
    final double[] results = strategy.score(scores);

    Assert.assertEquals(1.0, results[0], 0);
    Assert.assertEquals(2.0, results[1], 0);
    Assert.assertEquals(3.0, results[2], 0);
    Assert.assertEquals(4.0, results[3], 0);
    Assert.assertEquals(5.0, results[4], 0);

  }

  @Test
  public void calculateAverage2() {

    final List<double[]> scores = new LinkedList<>();
    scores.add(new double[]{2, 1, 5});
    scores.add(new double[]{4, 3, 10});
    scores.add(new double[]{6, 5, 15});

    final ClassificationScoringStrategy strategy = new AverageClassifcationScoringStrategy();
    final double[] results = strategy.score(scores);

    Assert.assertEquals(4.0, results[0], 0);
    Assert.assertEquals(3.0, results[1], 0);
    Assert.assertEquals(10.0, results[2], 0);

  }

}
