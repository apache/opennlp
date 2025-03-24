package opennlp.tools.monitoring;

/**  Identifies whether the difference between the training accuracy for current iteration
 *   and the training accuracy of previous
 *   n-1, n-2.. n-ith iterations is less than the defined Tolerance.
 *
 */
public class PrevNIterationAccuracyLessThanTolerance implements StopCriteria  {

    public static String ACCURACY_DIFF_UNDER_TOLERANCE = "Stopping: change in training set accuracy less than {%s}";

    private final double tolerance;

    /** TODO: 24-03-2025   : i) Should a generic test method be exposed at interface level?
     *       ii) iterationDeltaAccuracy could be a list?*/
    public boolean test (double ... iterationDeltaAccuracy) {
        return StrictMath.abs(iterationDeltaAccuracy[0]) < tolerance
                && StrictMath.abs(iterationDeltaAccuracy[1]) < tolerance
                && StrictMath.abs(iterationDeltaAccuracy[2]) < tolerance;
    }

    public PrevNIterationAccuracyLessThanTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public String getMessageIfSatisfied() {
        return String.format(ACCURACY_DIFF_UNDER_TOLERANCE, tolerance);
    }
}