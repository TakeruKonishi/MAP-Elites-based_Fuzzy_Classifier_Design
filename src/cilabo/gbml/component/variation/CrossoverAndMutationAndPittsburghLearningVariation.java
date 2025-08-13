package cilabo.gbml.component.variation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.uma.jmetal.component.variation.Variation;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.util.JMetalException;

import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;
import cilabo.utility.GeneralFunctions;

public class CrossoverAndMutationAndPittsburghLearningVariation<S extends PittsburghSolution<?>>
			implements Variation<S>
{
	private CrossoverOperator<S> crossover;
	private MutationOperator<S> mutation;
	private int matingPoolSize;
	private int offspringPopulationSize;

	public CrossoverAndMutationAndPittsburghLearningVariation(
			int offspringPopulationSize,
			CrossoverOperator<S> crossover,
			MutationOperator<S> mutation)
	{
		this.offspringPopulationSize = offspringPopulationSize;
		this.crossover = crossover;
		this.mutation = mutation;

		this.matingPoolSize = offspringPopulationSize *
				crossover.getNumberOfRequiredParents() / crossover.getNumberOfGeneratedChildren();

		int remainder = matingPoolSize % crossover.getNumberOfRequiredParents();
		if(remainder != 0) {
			matingPoolSize += remainder;
		}
	}

	@Override
	public List<S> variate(List<S> population, List<S> matingPopulation) {

		int numberOfParents = crossover.getNumberOfRequiredParents();

		List<S> matingCopy = matingPopulation.stream()
			    .map(sol -> (S) sol.copy())
			    .collect(Collectors.toList());

		checkNumberOfParents(matingCopy, numberOfParents);

		List<S> offspringPopulation = new ArrayList<>(offspringPopulationSize);
		for(int i = 0; i < matingPoolSize; i+= numberOfParents) {
			List<S> parents = new ArrayList<>(numberOfParents);
			for(int j = 0; j < numberOfParents; j++) {
				parents.add(matingCopy.get(i + j));
			}

			/* Crossover */
			List<S> offspring = crossover.execute(parents);
			if(!GeneralFunctions.checkRule((PittsburghSolution<MichiganSolution<?>>) offspring.get(0))) {
				System.err.println("crossover @" + this.getClass().getSimpleName());
			}

			for(S solution : offspring) {
				if(solution.getNumberOfVariables() < 1) {
					System.err.println("number Of Rules is less than 1 @" + this.getClass().getSimpleName());}
				/* Mutation */
				mutation.execute(solution);
				/* Learning */
				solution.learning();

				if(!GeneralFunctions.checkRule((PittsburghSolution<MichiganSolution<?>>) offspring.get(0))) {
					System.err.println("mutation @" + this.getClass().getSimpleName());
				}

				offspringPopulation.add(solution);
				if(offspringPopulation.size() == offspringPopulationSize) {
					break;
				}
			}
		}

		return offspringPopulation;
	}

	/**
	 * A crossover operator is applied to a number of parents, and it assumed that the population contains
	 * a valid number of population. This method checks that.
	 * @param population
	 * @param numberOfParentsForCrossover
	 */
	private void checkNumberOfParents(List<S> population, int numberOfParentsForCrossover) {
		if ((population.size() % numberOfParentsForCrossover) != 0) {
			throw new JMetalException(
					"Wrong number of parents: the remainder if the " +
					"population size (" + population.size() + ") is not divisible by " +
					numberOfParentsForCrossover);
		}
	}

	@Override
	public int getMatingPoolSize() {
		return this.matingPoolSize;
	}

	@Override
	public int getOffspringPopulationSize() {
		return this.offspringPopulationSize;
	}

	public CrossoverOperator<S> getCrossover() {
		return crossover;
	}

	public void setCrossover(CrossoverOperator<S> crossover) {
		this.crossover = crossover;
	}

	public MutationOperator<S> getMutation() {
		return mutation;
	}

	public void setMutation(MutationOperator<S> mutation) {
		this.mutation = mutation;
	}

	public void setMatingPoolSize(int matingPoolSize) {
		this.matingPoolSize = matingPoolSize;
	}

	public void setOffspringPopulationSize(int offspringPopulationSize) {
		this.offspringPopulationSize = offspringPopulationSize;
	}
}
