package cilabo.main.impl.MAPElites;

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
import org.uma.jmetal.util.observer.impl.EvaluationObserver;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import cilabo.data.DataSet;
import cilabo.data.DataSetManager;
import cilabo.data.Input;
import cilabo.data.pattern.impl.Pattern_Basic;
import cilabo.fuzzy.classifier.Classifier;
import cilabo.fuzzy.classifier.classification.Classification;
import cilabo.fuzzy.classifier.classification.impl.SingleWinnerRuleSelection;
import cilabo.fuzzy.classifier.impl.Classifier_basic;
import cilabo.fuzzy.knowledge.factory.HomoTriangleKnowledgeFactory;
import cilabo.fuzzy.knowledge.membershipParams.Parameters;
import cilabo.fuzzy.rule.Rule.RuleBuilder;
import cilabo.fuzzy.rule.antecedent.factory.impl.HeuristicRuleGenerationMethod;
import cilabo.fuzzy.rule.consequent.factory.impl.MoFGBML_Learning;
import cilabo.fuzzy.rule.impl.Rule_Basic;
import cilabo.gbml.objectivefunction.michigan.RuleLength;
import cilabo.gbml.objectivefunction.pittsburgh.ErrorRate;
import cilabo.gbml.objectivefunction.pittsburgh.NumberOfRules;
import cilabo.gbml.operator.crossover.HybridGBMLcrossover;
import cilabo.gbml.operator.crossover.MichiganCrossover;
import cilabo.gbml.operator.crossover.PittsburghCrossover;
import cilabo.gbml.operator.mutation.PittsburghMutation;
import cilabo.gbml.solution.michiganSolution.AbstractMichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.impl.PittsburghSolution_Basic;
import cilabo.main.Consts;
import cilabo.utility.Output;
import cilabo.utility.Parallel;
import cilabo.utility.Random;
import xml.XML_manager;

public class FGBML_MAPElites_Main {
	public static void main(String[] args) throws JMetalException, FileNotFoundException {
		String sep = File.separator;

		/* ********************************************************* */
		System.out.println();
		System.out.println("==== INFORMATION ====");
		System.out.println("main: " + FGBML_MAPElites_Main.class.getCanonicalName());
		String version = "1.0";
		System.out.println("version: " + version);
		System.out.println();
		System.out.println("Algorithm: Hybrid-style Fuzzy Genetics-based Machine Learning");
		System.out.println("IA: MAP-Elites");
		System.out.println();
		/* ********************************************************* */
		// Load consts.properties
		Consts.set("consts");
		// make result directory
		Output.mkdirs(Consts.ROOTFOLDER);


		// set command arguments to static variables
		FGBML_MAPElites_CommandLineArgs.loadArgs(FGBML_MAPElites_CommandLineArgs.class.getCanonicalName(), args);
		// Output constant parameters
		String fileName = Consts.EXPERIMENT_ID_DIR + sep + "Consts.txt";
		Output.writeln(fileName, Consts.getString(), true);
		Output.writeln(fileName, FGBML_MAPElites_CommandLineArgs.getParamsString(), true);
		XML_manager.getInstance().addElement(XML_manager.getInstance().getRoot(), Consts.toElement());

		// Initialize ForkJoinPool
		Parallel.getInstance().initLearningForkJoinPool(FGBML_MAPElites_CommandLineArgs.parallelCores);

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
		Input.loadTrainTestFiles_Basic(FGBML_MAPElites_CommandLineArgs.trainFile, FGBML_MAPElites_CommandLineArgs.testFile);
		DataSet<Pattern_Basic> train = (DataSet<Pattern_Basic>) DataSetManager.getInstance().getTrains().get(0);
		DataSet<Pattern_Basic> test = (DataSet<Pattern_Basic>) DataSetManager.getInstance().getTests().get(0);

		/** XML ファイル出力用インスタンスの生成*/
		XML_manager.getInstance();

		/* Run FGBML algorithm =============== */
		HybridStyleFGBML(train, test);
		/* ===================================== */

		try {
			XML_manager.getInstance().output(Consts.EXPERIMENT_ID_DIR);
		} catch (TransformerException | IOException e) {
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
	 */
	public static void HybridStyleFGBML (DataSet<Pattern_Basic> train, DataSet<Pattern_Basic> test) {
		Random.getInstance().initRandom(2022);
		String sep = File.separator;

		Parameters parameters = new Parameters(train);
		HomoTriangleKnowledgeFactory KnowledgeFactory = new HomoTriangleKnowledgeFactory(parameters);
		KnowledgeFactory.create2_3_4_5();

		List<Pair<Integer, Integer>> bounds_Michigan = AbstractMichiganSolution.makeBounds();
		int numberOfObjectives_Michigan = 1;
		int numberOfConstraints_Michigan = 0;

		int numberOfvariables_Pittsburgh = Consts.INITIATION_RULE_NUM;
		int numberOfObjectives_Pittsburgh = 1;
		int numberOfConstraints_Pittsburgh = 0;

		RuleBuilder<Rule_Basic, ?, ?> ruleBuilder = new Rule_Basic.RuleBuilder_Basic(
				new HeuristicRuleGenerationMethod(train),
				new MoFGBML_Learning(train));

		MichiganSolutionBuilder<MichiganSolution_Basic<Rule_Basic>> michiganSolutionBuilder
			= new MichiganSolution_Basic.MichiganSolutionBuilder_Basic<Rule_Basic>(
					bounds_Michigan,
					numberOfObjectives_Michigan,
					numberOfConstraints_Michigan,
					ruleBuilder);

		Classification<MichiganSolution_Basic<Rule_Basic>> classification = new SingleWinnerRuleSelection<MichiganSolution_Basic<Rule_Basic>>();

		Classifier<MichiganSolution_Basic<Rule_Basic>> classifier = new Classifier_basic<>(classification);

		/* Optimization Problem */
		Problem<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> problem =
				new PittsburghFGBML_MAPElites<MichiganSolution_Basic<Rule_Basic>>(
						numberOfvariables_Pittsburgh,
						numberOfObjectives_Pittsburgh,
						numberOfConstraints_Pittsburgh,
						train,
						michiganSolutionBuilder,
						classifier);


		/* Crossover: Hybrid-style GBML specific crossover operator. */
		double crossoverProbability = 1.0;

		/* Michigan operation */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> michiganX
				= new MichiganCrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>, MichiganSolution_Basic<Rule_Basic>>(Consts.MICHIGAN_CROSS_RT, train);
		/* Pittsburgh operation */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> pittsburghX
				= new PittsburghCrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>, MichiganSolution_Basic<Rule_Basic>>(Consts.PITTSBURGH_CROSS_RT);
		/* Hybrid-style crossover */
		CrossoverOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> crossover
				= new HybridGBMLcrossover<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>, MichiganSolution_Basic<Rule_Basic>>(crossoverProbability, Consts.MICHIGAN_OPE_RT, michiganX, pittsburghX);
		/* Mutation: Pittsburgh-style GBML specific mutation operator. */
		MutationOperator<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> mutation
				= new PittsburghMutation<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>, MichiganSolution_Basic<Rule_Basic>>(train);

		/* Termination: Number of total evaluations */
		Termination termination = new TerminationByEvaluations(Consts.TERMINATE_EVALUATION);


		/* Algorithm: Hybrid-style FGBML with MAP-Elites */
		HybridFGBMLwithMAPElites<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> algorithm
			= new HybridFGBMLwithMAPElites<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>(problem,
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

		/* Final solutions in final generation */
		List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> FinalSolutions = algorithm.getPopulation();

		//Results of final generation
	    ArrayList<String> strs = new ArrayList<>();
	    String str = "pop,train,NR,RL,Cover,RW,test";
	    strs.add(str);

	    for(int i = 0; i < FinalSolutions.size(); i++) {
            double errorRatetrain = FinalSolutions.get(i).getObjective(0);
        	NumberOfRules<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> RuleNumFunc = new NumberOfRules<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
            double ruleNum = RuleNumFunc.function(FinalSolutions.get(i));
            RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFunc = new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
            double TotalRuleLength = 0;
            for (int j = 0; j < FinalSolutions.get(i).getNumberOfVariables(); j++) {
                 double RuleLength = RuleLengthFunc.function(FinalSolutions.get(i).getVariable(j));
                 TotalRuleLength += RuleLength;
            }

            double TotalCover = 0;
            for (int j = 0; j < FinalSolutions.get(i).getNumberOfVariables(); j++) {

            	 double Cover = 0;
            	 List<Double> support = new ArrayList<Double>();

            	 for (int k = 0; k < train.getNdim(); k++) {
            		  if (FinalSolutions.get(i).getVariable(j).getVariable(k) != 0) {

            			  if ((FinalSolutions.get(i).getVariable(j).getVariable(k) == 1) ||
            				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 2) ||
            				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 4)){
            				   support.add(1.0);
            			  }else if ((FinalSolutions.get(i).getVariable(j).getVariable(k) == 3) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 5) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 11) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 12) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 13)){
                				   support.add(1.0/2);
            			  }else if ((FinalSolutions.get(i).getVariable(j).getVariable(k) == 6) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 9)){
                				   support.add(1.0/3);
            			  }else if ((FinalSolutions.get(i).getVariable(j).getVariable(k) == 7) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 8)){
           				   support.add(2.0/3);
       			          }else if ((FinalSolutions.get(i).getVariable(j).getVariable(k) == 10) ||
                				  (FinalSolutions.get(i).getVariable(j).getVariable(k) == 14)){
           				   support.add(1.0/4);
       			          }
            		  }
            	 }
            	 if (!support.isEmpty()) {
            	     Cover = support.stream().reduce(1.0, (a, b) -> a * b);
                   	 TotalCover += Cover;
                 }
            }

            double TotalRW = 0;
            for (int j = 0; j < FinalSolutions.get(i).getNumberOfVariables(); j++) {
                double RW = (Double) FinalSolutions.get(i).getVariable(j).getRuleWeight().getRuleWeightValue();
                TotalRW += RW;
            }
            double AveRW = TotalRW/(FinalSolutions.get(i).getNumberOfVariables());


            ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> function1
			= new ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
		    double errorRatetest = function1.function(FinalSolutions.get(i), test);

	    	str = String.valueOf(i);
	    	str += "," + errorRatetrain;
	    	str += "," + ruleNum;
	    	str += "," + TotalRuleLength;
	    	str += "," + TotalCover;
	    	str += "," + AveRW;
	    	str += "," + errorRatetest;
	    	strs.add(str);
	    }
	    String fileName = Consts.EXPERIMENT_ID_DIR + sep + "results.csv";
	    Output.writeln(fileName, strs, false);

		return;
	}
}