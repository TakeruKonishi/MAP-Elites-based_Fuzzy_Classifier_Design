package cilabo.gbml.objectivefunction.pittsburgh;

import cilabo.data.DataSet;
import cilabo.data.pattern.Pattern;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;

/**
 * Error rate evaluation function (No side effects).
 * - Rejected (winnerSolution == null) is treated as an error.
 * - Does NOT write any attributes to solutions/rules.
 *
 * @author Takeru Konishi
 *
 * @param <S>
 */
public final class ErrorRateNoSideEffect<S extends PittsburghSolution<?>> {

    public ErrorRateNoSideEffect() {}

    /**
     * Compute error rate without mutating the solution or its rules.
     * @param solution (PittsburghSolution)
     * @param data to evaluate
     * @return error rate in [0, 1]
     */
    public double function(S solution, DataSet<?> data) {
        int numberOfErrorPatterns = 0;

        for (int i = 0; i < data.getDataSize(); i++) {
            Pattern<?> pattern = data.getPattern(i);
            MichiganSolution<?> winnerSolution = solution.classify(pattern);

            // Treat rejected output as an error
            if (winnerSolution == null) {
                numberOfErrorPatterns++;
                continue;
            }

            // Misclassification
            if (!pattern.getTargetClass().equalsClassLabel(winnerSolution.getClassLabel())) {
                numberOfErrorPatterns++;
            }
        }

		double errorRate = numberOfErrorPatterns / (double)data.getDataSize();
		return errorRate;
    }
}
