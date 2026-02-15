package cilabo.main.impl.MAPElites;

import java.util.List;

import org.uma.jmetal.problem.Problem;

import cilabo.data.DataSet;
import cilabo.fuzzy.classifier.Classifier;
import cilabo.gbml.objectivefunction.pittsburgh.ErrorRate;
import cilabo.gbml.problem.pittsburghFGBML_Problem.AbstractPittsburghFGBML;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import cilabo.gbml.solution.pittsburghSolution.impl.PittsburghSolution_Basic;

/**
 * cilabo.gbml.problem.pittsburghFGBML_Problem.impl.PittsburghFGBML_; を参考に作成
 *
 */
public class PittsburghMEFC<michiganSolution extends MichiganSolution<?>>
        extends AbstractPittsburghFGBML<PittsburghSolution_Basic<michiganSolution>, michiganSolution> implements Problem<PittsburghSolution_Basic<michiganSolution>> {

    public PittsburghMEFC(
        	int numberOfVariables,
        	int numberOfObjectives,
        	int numberOfConstraints,
        	DataSet<?> train,
        	MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder,
        	Classifier<michiganSolution> classifier) {
        super(numberOfVariables, numberOfObjectives, numberOfConstraints,
        			train, michiganSolutionBuilder, classifier);
        this.setName("PittsburghMEFC");
    }

    @Override
   	public void evaluate(PittsburghSolution_Basic<michiganSolution> solution) {
    	/* The first objective */
		ErrorRate<PittsburghSolution_Basic<michiganSolution>> function1 = new ErrorRate<PittsburghSolution_Basic<michiganSolution>>();
		double f1 = function1.function(solution, train);

		solution.setObjective(0, f1);
   	}

	@Override
	public PittsburghSolution_Basic<michiganSolution> createSolution() {
		PittsburghSolution_Basic<michiganSolution> pittsburghSolution = new PittsburghSolution_Basic<michiganSolution>(
				this.getNumberOfVariables(),
				this.getNumberOfObjectives(),
				this.getNumberOfConstraints(),
				this.michiganSolutionBuilder.copy(),
				this.classifier.copy());

		List<michiganSolution> solutionArray = this.michiganSolutionBuilder.createMichiganSolutions(this.getNumberOfVariables());
		for(int i=0; i<this.getNumberOfVariables(); i++) {
			pittsburghSolution.setVariable(i, solutionArray.get(i));
		}
		return pittsburghSolution;
	}

	@Override
	public String toString() {
		return "PittsburghMEFC [michiganSolutionBuilder=" + michiganSolutionBuilder
				+ ", classifier=" + classifier + "]";
	}

}
