package cilabo.main.impl.multiTasking;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.tuple.Pair;
import org.uma.jmetal.component.termination.Termination;
import org.uma.jmetal.component.termination.impl.TerminationByEvaluations;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.fileoutput.SolutionListOutput;
import org.uma.jmetal.util.fileoutput.impl.DefaultFileOutputContext;
import org.uma.jmetal.util.observer.impl.EvaluationObserver;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import cilabo.data.DataSet;
import cilabo.data.DataSetManager;
import cilabo.data.Input;
import cilabo.data.pattern.impl.Pattern_MultiClass;
import cilabo.fuzzy.classifier.Classifier;
import cilabo.fuzzy.classifier.classification.Classification;
import cilabo.fuzzy.classifier.classification.impl.SingleWinnerRuleSelection;
import cilabo.fuzzy.classifier.impl.Classifier_basic;
import cilabo.fuzzy.knowledge.Knowledge;
import cilabo.fuzzy.knowledge.factory.HomoTriangleKnowledgeFactory;
import cilabo.fuzzy.knowledge.membershipParams.Parameters;
import cilabo.fuzzy.rule.Rule.RuleBuilder;
import cilabo.fuzzy.rule.antecedent.factory.impl.HeuristicRuleGenerationMethod;
import cilabo.fuzzy.rule.consequent.factory.impl.MoFGBML_Learning_MultiLabel;
import cilabo.fuzzy.rule.impl.Rule_MultiClass;
import cilabo.gbml.algorithm.HybridMoFGBMLwithNSGAII;
import cilabo.gbml.objectivefunction.pittsburgh.ErrorRate;
import cilabo.gbml.operator.crossover.HybridGBMLcrossover;
import cilabo.gbml.operator.crossover.MichiganCrossover;
import cilabo.gbml.operator.crossover.PittsburghCrossover;
import cilabo.gbml.operator.mutation.PittsburghMutation;
import cilabo.gbml.problem.pittsburghFGBML_Problem.impl.PittsburghFGBML_Basic;
import cilabo.gbml.solution.michiganSolution.AbstractMichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.impl.PittsburghSolution_Basic;
import cilabo.main.Consts;
import cilabo.utility.Output;
import cilabo.utility.Parallel;
import cilabo.utility.Random;
import xml.XML_manager;

public class MultiTasking_Main {


	public static void main(String[] args) throws JMetalException, FileNotFoundException {
		String sep = File.separator;

		/* ********************************************************* */
		System.out.println();
		System.out.println("==== INFORMATION ====");
		System.out.println("main: " + MultiTasking_Main.class.getCanonicalName());
		String version = "1.0";
		System.out.println("version: " + version);
		System.out.println();
		System.out.println("Algorithm: Hybrid-style Multiobjective Fuzzy Genetics-Based Machine Learning");
		System.out.println("EMOA: NSGA-II");
		System.out.println();
		/* ********************************************************* */
		// Load consts.properties
		Consts.set("consts");
		// make result directory
		Output.mkdirs(Consts.ROOTFOLDER);

		// set command arguments to static variables
		MultiTasking_CommandLineArgs.loadArgs(MultiTasking_CommandLineArgs.class.getCanonicalName(), args);
		// Output constant parameters
		String fileName = Consts.EXPERIMENT_ID_DIR + sep + "Consts.txt";
		Output.writeln(fileName, Consts.getString(), true);
		Output.writeln(fileName, MultiTasking_CommandLineArgs.getParamsString(), true);

		// Initialize ForkJoinPool
		Parallel.getInstance().initLearningForkJoinPool(MultiTasking_CommandLineArgs.parallelCores);

		System.out.println("Processors: " + Runtime.getRuntime().availableProcessors() + " ");
		System.out.print("args: ");
		for(int i = 0; i < args.length; i++) {
			System.out.print(args[i] + " ");
		}


		System.out.println();
		System.out.println("=====================");
		System.out.println();

		/* ********************************************************* */
		System.out.println("==== EXPERIMENT =====");
		Date start = new Date();
		System.out.println("START: " + start);

		/* Random Number ======================= */
		Random.getInstance().initRandom(Consts.RAND_SEED);
		JMetalRandom.getInstance().setSeed(Consts.RAND_SEED);

		/* Load Dataset ======================== */
		Input.loadTrainTestFiles_Basic(MultiTasking_CommandLineArgs.trainFile, MultiTasking_CommandLineArgs.testFile);
		DataSet<Pattern_MultiClass> test = (DataSet<Pattern_MultiClass>) DataSetManager.getInstance().getTests().get(0);
		DataSet<Pattern_MultiClass> train = (DataSet<Pattern_MultiClass>) DataSetManager.getInstance().getTrains().get(0);


		/** XML ファイル出力ようインスタンスの生成*/
		XML_manager.getInstance();

		/* Run MoFGBML algorithm =============== */
		MultiTaskingMoFGBML(train, test);
		/* ===================================== */

		try {
			XML_manager.getInstance().output(Consts.EXPERIMENT_ID_DIR);
		} catch (TransformerException | IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		Date end = new Date();
		System.out.println("END: " + end);
		System.out.println("=====================");
		/* ********************************************************* */

		System.exit(0);
	}

	/**
	 *
	 * @param train
	 * @param test
	 */
	public static void MultiTaskingMoFGBML(DataSet<Pattern_MultiClass> train, DataSet<Pattern_MultiClass> test) {
		Random.getInstance().initRandom(2022);
		String sep = File.separator;

		int dimension = train.getNdim();
		Parameters parameters = new Parameters(train);
		HomoTriangleKnowledgeFactory KnowledgeFactory = new HomoTriangleKnowledgeFactory(parameters);
		KnowledgeFactory.create2_3_4_5();

		List<Pair<Integer, Integer>> bounds_Michigan = AbstractMichiganSolution.makeBounds();
		int numberOfObjectives_Michigan = 2;
		int numberOfConstraints_Michigan = 0;

		int numberOfvariables_Pittsburgh = Consts.INITIATION_RULE_NUM;
		int numberOfObjectives_Pittsburgh = 2;
		int numberOfConstraints_Pittsburgh = 0;

		RuleBuilder<Rule_MultiClass, ?, ?> ruleBuilder = new Rule_MultiClass.RuleBuilder_MultiClas(
				new HeuristicRuleGenerationMethod(train),
				new MoFGBML_Learning_MultiLabel(train));

		MichiganSolutionBuilder<MichiganSolution_Basic<Rule_MultiClass>> michiganSolutionBuilder
			= new MichiganSolution_Basic.MichiganSolutionBuilder_Basic<Rule_MultiClass>(
					bounds_Michigan,
					numberOfObjectives_Michigan,
					numberOfConstraints_Michigan,
					ruleBuilder);

		Classification<MichiganSolution_Basic<Rule_MultiClass>> classification = new SingleWinnerRuleSelection<MichiganSolution_Basic<Rule_MultiClass>>();

		Classifier<MichiganSolution_Basic<Rule_MultiClass>> classifier = new Classifier_basic<>(classification);

		/* MOP: Multi-objective Optimization Problem */
		Problem<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> problem =
				new PittsburghFGBML_Basic<MichiganSolution_Basic<Rule_MultiClass>>(
						numberOfvariables_Pittsburgh,
						numberOfObjectives_Pittsburgh,
						numberOfConstraints_Pittsburgh,
						train,
						michiganSolutionBuilder,
						classifier);


		/* Crossover: Hybrid-style GBML specific crossover operator. */
		double crossoverProbability = 1.0;

		/* Michigan operation */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> michiganX
				= new MichiganCrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>, MichiganSolution_Basic<Rule_MultiClass>>(Consts.MICHIGAN_CROSS_RT, train);
		/* Pittsburgh operation */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> pittsburghX
				= new PittsburghCrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>, MichiganSolution_Basic<Rule_MultiClass>>(Consts.PITTSBURGH_CROSS_RT);
		/* Hybrid-style crossover */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> crossover
				= new HybridGBMLcrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>, MichiganSolution_Basic<Rule_MultiClass>>(crossoverProbability, Consts.MICHIGAN_OPE_RT, michiganX, pittsburghX);
		/* Mutation: Pittsburgh-style GBML specific mutation operator. */
		MutationOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> mutation
				= new PittsburghMutation<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>, MichiganSolution_Basic<Rule_MultiClass>>(train);

		/* Termination: Number of total evaluations */
		Termination termination = new TerminationByEvaluations(Consts.TERMINATE_EVALUATION);

		//knowlwdge出力用
		XML_manager.getInstance().addElement(XML_manager.getInstance().getRoot(), Knowledge.getInstance(). toElement());

		/* Algorithm: Hybrid-style MoFGBML with NSGA-II */
		HybridMoFGBMLwithNSGAII<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> algorithm
			= new HybridMoFGBMLwithNSGAII<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>>(train, problem,
											Consts.POPULATION_SIZE,
											Consts.OFFSPRING_POPULATION_SIZE,
											Consts.OUTPUT_FREQUENCY,
											Consts.EXPERIMENT_ID_DIR,
											crossover,
											mutation,
											termination);

		/* Running observation */
		EvaluationObserver evaluationObserver = new EvaluationObserver(Consts.OUTPUT_FREQUENCY);
		algorithm.getObservable().register(evaluationObserver);

		/* === GA RUN === */
		algorithm.run();
		/* ============== */

		/* Non-dominated solutions in final generation */
		List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> nonDominatedSolutions = algorithm.getResult();
	    new SolutionListOutput(nonDominatedSolutions)
        	.setVarFileOutputContext(new DefaultFileOutputContext(Consts.EXPERIMENT_ID_DIR+sep+"VAR.csv", ","))
        	.setFunFileOutputContext(new DefaultFileOutputContext(Consts.EXPERIMENT_ID_DIR+sep+"FUN.csv", ","))
        	.print();

	    // Test data
	    ArrayList<String> strs = new ArrayList<>();
	    String str = "pop,test";
	    strs.add(str);

	    for(int i = 0; i < nonDominatedSolutions.size(); i++) {
	    	PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>> solution = nonDominatedSolutions.get(i);
			ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>> function1
				= new ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_MultiClass>>>();
			double errorRate = function1.function(solution, test);

	    	str = String.valueOf(i);
	    	str += "," + errorRate;
	    	strs.add(str);
	    }
	    String fileName = Consts.EXPERIMENT_ID_DIR + sep + "results.csv";
	    Output.writeln(fileName, strs, false);

		return;
	}
}
