package cilabo.gbml.problem.pittsburghFGBML_Problem;

import org.uma.jmetal.problem.AbstractGenericProblem;
import org.uma.jmetal.problem.Problem;

import cilabo.data.DataSet;
import cilabo.fuzzy.classifier.Classifier;
import cilabo.fuzzy.rule.impl.Rule_Basic;
import cilabo.gbml.objectivefunction.michigan.RuleLength;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;
import cilabo.gbml.solution.util.attribute.NumberOfWinner;

public abstract class AbstractPittsburghFGBML <pittsburghSolutionObject extends PittsburghSolution<michiganSolution>,
		michiganSolution extends MichiganSolution<?>>
		extends AbstractGenericProblem <pittsburghSolutionObject> implements Problem<pittsburghSolutionObject>{

	protected DataSet<?> train;
	protected MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder;

	/** 識別方式
	 * @see cilabo.fuzzy.classifier.classification */
	protected Classifier<michiganSolution> classifier;

	/** Constructor */
	public AbstractPittsburghFGBML(
			int numberOfVariables,
			int numberOfObjectives,
			int numberOfConstraints,
			DataSet<?> train,
			MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder,
			Classifier<michiganSolution> classifier) {
		setNumberOfVariables(numberOfVariables);
		setNumberOfObjectives(numberOfObjectives);
		setNumberOfConstraints(numberOfConstraints);
		this.train = train;
		this.michiganSolutionBuilder = michiganSolutionBuilder;
		this.classifier = classifier;
	}


	public void removeNoWinnerMichiganSolution(PittsburghSolution<michiganSolution> solution) {
		if(solution.getNumberOfVariables() == 0) {throw new ArithmeticException("This PittsburghSolution has no michiganSolution");}
		for(int i=0; i<solution.getNumberOfVariables(); i++) {
			if((int)solution.getVariable(i).getAttribute((new NumberOfWinner<PittsburghSolution<michiganSolution>>()).getAttributeId()) < 1) {
				solution.removeVariable(i); i--;
			}
		}
		/*NumberOfRules<PittsburghSolution<michiganSolution>> function2 = new NumberOfRules<PittsburghSolution<michiganSolution>>();
		double f2 = function2.function(solution);
		solution.setObjective(OBJECTIVES_FOR_PITTSBURGH.NumberOfRule.toInt(), f2);*/

		RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFunc = new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
        double TotalRuleLength = 0;
        for (int i = 0; i < solution.getNumberOfVariables(); i++) {
             double RuleLength = RuleLengthFunc.function((MichiganSolution_Basic<Rule_Basic>) solution.getVariable(i));
             TotalRuleLength += RuleLength;
        }
		double f2 = TotalRuleLength;
		//第何目的関数かに注意
		solution.setObjective(1, f2);

		if(solution.getNumberOfVariables() == 0) {
			throw new ArithmeticException("PittsburghSolution has no winner michiganSolution");
		}
	}

	public DataSet<?> getTrain() {
		return train;
	}

	public MichiganSolutionBuilder<michiganSolution> getMichiganSolutionBuilder() {
		return michiganSolutionBuilder;
	}

	public Classifier<michiganSolution> getClassifier() {
		return classifier;
	}
}
