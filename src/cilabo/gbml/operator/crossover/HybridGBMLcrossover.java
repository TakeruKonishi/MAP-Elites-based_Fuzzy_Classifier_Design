package cilabo.gbml.operator.crossover;

import java.util.ArrayList;
import java.util.List;

import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.checking.Check;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;
import cilabo.gbml.solution.util.EqualsSolution;
import cilabo.utility.GeneralFunctions;

public class HybridGBMLcrossover <pittsburghSolution extends PittsburghSolution<michiganSolution>, michiganSolution extends MichiganSolution<?>>
	implements CrossoverOperator<pittsburghSolution> {

	private double crossoverProbability;
	private RandomGenerator<Double> crossoverRandomGenerator;
	BoundedRandomGenerator<Integer> selectRandomGenerator;

	private double michiganOperationProbability;
	CrossoverOperator<pittsburghSolution> michiganX;
	CrossoverOperator<pittsburghSolution> pittsburghX;

	private static final String ATTR_USED_PARENT2 = "USED_PARENT2";

	/** Constructor */
	public HybridGBMLcrossover(double crossoverProbability, double michiganOperationProbability,
							   CrossoverOperator<pittsburghSolution> michiganX, CrossoverOperator<pittsburghSolution> pittsburghX) {
		this(crossoverProbability,
			 () -> JMetalRandom.getInstance().nextDouble(),
			 (a, b) -> JMetalRandom.getInstance().nextInt(a, b));

		this.michiganOperationProbability = michiganOperationProbability;
		this.michiganX = michiganX;
		this.pittsburghX = pittsburghX;
	}

	/** Constructor */
	public HybridGBMLcrossover(double crossoverProbability, RandomGenerator<Double> randomGenerator) {
		this(crossoverProbability,
			 randomGenerator,
			 BoundedRandomGenerator.fromDoubleToInteger(randomGenerator));
	}

	/** Constructor */
	public HybridGBMLcrossover(double crossoverProbability,
			RandomGenerator<Double> crossoverRandomGenerator,
			BoundedRandomGenerator<Integer> selectRandomGenerator)
	{
		if(crossoverProbability < 0) {
			throw new JMetalException("Crossover probability is negative: " + crossoverProbability);
		}
		this.crossoverProbability = crossoverProbability;
		this.crossoverRandomGenerator = crossoverRandomGenerator;
		this.selectRandomGenerator = selectRandomGenerator;
	}

	/* Getter */
	@Override
	public double getCrossoverProbability() {
		return this.crossoverProbability;
	}

	/* Setter */
	public void setCrossoverProbability(double crossoverProbability) {
		this.crossoverProbability = crossoverProbability;
	}

	@Override
	public int getNumberOfRequiredParents() {
		return 2;
	}

	@Override
	public int getNumberOfGeneratedChildren() {
		return 1;
	}

	@Override
	public List<pittsburghSolution> execute(List<pittsburghSolution> solutions) {
		Check.isNotNull(solutions);
		Check.that(solutions.size() == 2, "There must be two parents instead of " + solutions.size());
		return doCrossover(crossoverProbability, solutions.get(0), solutions.get(1));
	}

	public List<pittsburghSolution> doCrossover(
			double probability, pittsburghSolution parent1, pittsburghSolution parent2)
	{
		List<pittsburghSolution> offspring = new ArrayList<>();

		boolean usedParent2 = false;

		if(crossoverRandomGenerator.getRandomValue() < probability) {/* Do crossover */
			/* Judge if two parents are same. */
			double p;
			if(EqualsSolution.equalsPittsburghSolution(parent1, parent2)) p = 1.0;
			else p = michiganOperationProbability;

			if(crossoverRandomGenerator.getRandomValue() < p) {
				/* Michigan operation */
				List<pittsburghSolution> parents = new ArrayList<>();
				parents.add((pittsburghSolution) parent1.copy());
				offspring = michiganX.execute(parents);
				usedParent2 = false;
				if(!GeneralFunctions.checkRule((PittsburghSolution<MichiganSolution<?>>) offspring.get(0))) {
					System.err.println("michiganX");
				}
			}
			else {
				/* Pittsburgh operation */
				List<pittsburghSolution> parents = new ArrayList<>();
				parents.add((pittsburghSolution) parent1.copy());
				parents.add((pittsburghSolution) parent2.copy());
				offspring = pittsburghX.execute(parents);
				usedParent2 = true;
				if(!GeneralFunctions.checkRule((PittsburghSolution<MichiganSolution<?>>) offspring.get(0))) {
					System.err.println("pittsburghX");
				}
			}
		}
		else {/* Don't crossover */
			offspring.add((pittsburghSolution) parent1.copy());
			offspring.add((pittsburghSolution) parent2.copy());
			int index = selectRandomGenerator.getRandomValue(0,  1);
			usedParent2 = (index == 0);
			offspring.remove(index);
		}

		offspring.get(0).setAttribute(ATTR_USED_PARENT2, usedParent2);

		if(offspring.get(0).getNumberOfVariables() < 1) {System.err.println("incorrect input: number Of Rules is less than 1 @" + this.getClass().getSimpleName());}
		return offspring;
	}

}
