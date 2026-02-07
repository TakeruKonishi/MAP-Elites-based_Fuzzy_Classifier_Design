package cilabo.gbml.objectivefunction.pittsburgh;

import cilabo.data.DataSet;
import cilabo.data.pattern.Pattern;
import cilabo.fuzzy.rule.impl.Rule_Basic;
import cilabo.gbml.objectivefunction.michigan.RuleLength;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;

/**
 * @author Takeru Konishi
 *
 */
public final class AverageSingleWinnerRuleLength <S extends PittsburghSolution<?>>{

	public AverageSingleWinnerRuleLength() {}

	/**
	 * @param solution
	 * @param dataset
	 * @return double
	 */
	public double function(S solution, DataSet<?> dataset) {

		RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFunc = new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
		double TotalRuleLength = 0;
		int counted = 0;

		for(int p = 0; p < dataset.getDataSize(); p++) {

			Pattern<?> pattern = dataset.getPattern(p);

			MichiganSolution<?> winnerSolution = solution.classify(pattern);

			// rejectedならば次のパターン
			if(winnerSolution == null) {
				continue;
			}

			double RuleLength = RuleLengthFunc.function((MichiganSolution_Basic<Rule_Basic>) winnerSolution);
            TotalRuleLength += RuleLength;
            counted++;
		}

		double ASWRL = (counted == 0) ? 0.0 : (TotalRuleLength/counted);
		return ASWRL;
	}
}
