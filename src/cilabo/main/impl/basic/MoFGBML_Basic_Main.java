package cilabo.main.impl.basic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.tuple.Pair;
import org.uma.jmetal.component.termination.Termination;
import org.uma.jmetal.component.termination.impl.TerminationByEvaluations;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.fileoutput.impl.DefaultFileOutputContext;
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
import cilabo.gbml.algorithm.HybridMoFGBMLwithNSGAII;
import cilabo.gbml.objectivefunction.michigan.RuleLength;
import cilabo.gbml.objectivefunction.pittsburgh.AverageSingleWinnerRuleLength;
import cilabo.gbml.objectivefunction.pittsburgh.ErrorRate;
import cilabo.gbml.objectivefunction.pittsburgh.ErrorRateNoSideEffect;
import cilabo.gbml.objectivefunction.pittsburgh.NumberOfRules;
import cilabo.gbml.operator.crossover.HybridGBMLcrossover;
import cilabo.gbml.operator.crossover.MichiganCrossover;
import cilabo.gbml.operator.crossover.PittsburghCrossover;
import cilabo.gbml.operator.mutation.PittsburghMutation;
import cilabo.gbml.problem.pittsburghFGBML_Problem.impl.PittsburghFGBML_Basic;
import cilabo.gbml.problem.pittsburghFGBML_Problem.impl.PittsburghFGBML_NR_ASWRL;
import cilabo.gbml.solution.michiganSolution.AbstractMichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.impl.PittsburghSolution_Basic;
import cilabo.main.Consts;
import cilabo.main.ExperienceParameter.OBJECTIVES_FOR_PITTSBURGH;
import cilabo.util.fileoutput.PittsburghSolutionListOutputX;
import cilabo.utility.Output;
import cilabo.utility.Parallel;
import cilabo.utility.Random;
import xml.XML_manager;

/**
 * @version 1.0
 *
 *
 */
public class MoFGBML_Basic_Main {
	public static void main(String[] args) throws JMetalException, FileNotFoundException {
		String sep = File.separator;

		/* ********************************************************* */
		System.out.println();
		System.out.println("==== INFORMATION ====");
		System.out.println("main: " + MoFGBML_Basic_Main.class.getCanonicalName());
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
		MoFGBML_Basic_CommandLineArgs.loadArgs(MoFGBML_Basic_CommandLineArgs.class.getCanonicalName(), args);
		// Output constant parameters
		String fileName = Consts.EXPERIMENT_ID_DIR + sep + "Consts.txt";
		Output.writeln(fileName, Consts.getString(), true);
		Output.writeln(fileName, MoFGBML_Basic_CommandLineArgs.getParamsString(), true);
		XML_manager.getInstance().addElement(XML_manager.getInstance().getRoot(), Consts.toElement());

		// Initialize ForkJoinPool
		Parallel.getInstance().initLearningForkJoinPool(MoFGBML_Basic_CommandLineArgs.parallelCores);

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
		Input.loadTrainTestFiles_Basic(MoFGBML_Basic_CommandLineArgs.trainFile, MoFGBML_Basic_CommandLineArgs.testFile);
		DataSet<Pattern_Basic> train = (DataSet<Pattern_Basic>) DataSetManager.getInstance().getTrains().get(0);
		DataSet<Pattern_Basic> test = (DataSet<Pattern_Basic>) DataSetManager.getInstance().getTests().get(0);

		/** XML ファイル出力用インスタンスの生成*/
		XML_manager.getInstance();

		/* Run MoFGBML algorithm =============== */
		HybridStyleMoFGBML(train, test);
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
	public static void HybridStyleMoFGBML (DataSet<Pattern_Basic> train, DataSet<Pattern_Basic> test) {
		Random.getInstance().initRandom(Consts.RAND_SEED);
		String sep = File.separator;

		Parameters parameters = new Parameters(train);
		HomoTriangleKnowledgeFactory KnowledgeFactory = new HomoTriangleKnowledgeFactory(parameters);
		KnowledgeFactory.create2_3_4_5();

		List<Pair<Integer, Integer>> bounds_Michigan = AbstractMichiganSolution.makeBounds();
		int numberOfObjectives_Michigan = 1;
		int numberOfConstraints_Michigan = 0;

		int numberOfvariables_Pittsburgh = Consts.INITIATION_RULE_NUM;
		int numberOfObjectives_Pittsburgh = 3;
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

		/* MOP: Multi-objective Optimization Problem */
		Problem<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> problem =
				new PittsburghFGBML_NR_ASWRL<MichiganSolution_Basic<Rule_Basic>>(
						numberOfvariables_Pittsburgh,
						3,
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


		/* Algorithm: Hybrid-style MoFGBML with NSGA-II */
		HybridMoFGBMLwithNSGAII<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> algorithm
			= new HybridMoFGBMLwithNSGAII<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>(train, problem,
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
		List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> nonDominatedSolutions = algorithm.getResult();

		/* archive population */
		Set<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ARC = algorithm.getArchivePopulation();

		List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ARCList = new ArrayList<>(ARC);

		/*アーカイブから非劣解を抽出（分割なしversion）*/
		//List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> nonDominatedSolutionsARC = SolutionListUtils.getNonDominatedSolutions(ARCList);

		/*アーカイブから非劣解を抽出（分割ありversion）*/
		//サブリスト数（暫定で100に設定）
		int numberOfSublists = 100;

		//サブリストを格納するリスト
		List<List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>> partitionedList = new ArrayList<>();

		//分割に用いるパラメータの算出
		int totalSize = ARCList.size();
        int chunkSize = totalSize / numberOfSublists;
        int remainder = totalSize % numberOfSublists;
        int start = 0;

        // 元のリストの要素をサブリストに分割
        for (int i = 0; i < numberOfSublists; i++) {
            int end = start + chunkSize + (i < remainder ? 1 : 0);
            partitionedList.add(new ArrayList<>(ARCList.subList(start, end)));
            start = end;
        }

        // partitionedList内の各サブリストにgetNonDominatedSolutionsを適用し、結果を統合
        List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> mergedList = partitionedList.stream()
                .flatMap(list -> SolutionListUtils.getNonDominatedSolutions(list).stream())
                .collect(Collectors.toList());

        //統合後のリストから非劣解を抽出し，最終的な個体群とする
        List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> nonDominatedSolutionsARC = SolutionListUtils.getNonDominatedSolutions(mergedList);

		String outputRootDir = Consts.EXPERIMENT_ID_DIR;
		new PittsburghSolutionListOutputX(nonDominatedSolutionsARC)
		.setVarFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("VARARC-%d.csv", Consts.TERMINATE_EVALUATION), ","))
		.setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUNARC-%d.csv", Consts.TERMINATE_EVALUATION), ","))
        .print();

        //バグ含むのでコメントアウト（修正するならJmetal仕様のメソッドを書き換える）
		/*new SolutionListOutput(nonDominatedSolutions)
    	.setVarFileOutputContext(new DefaultFileOutputContext(Consts.EXPERIMENT_ID_DIR+sep+"VAR-final.csv", ","))
    	.setFunFileOutputContext(new DefaultFileOutputContext(Consts.EXPERIMENT_ID_DIR+sep+"FUN-final.csv", ","))
    	.print();*/

	    // Test data（Resultsに集約したためコメントアウト）
	    /*ArrayList<String> strs = new ArrayList<>();
	    String str = "pop,test";
	    strs.add(str);

	    for(int i = 0; i < nonDominatedSolutions.size(); i++) {
	    	PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>> solution = nonDominatedSolutions.get(i);
			ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> function1
				= new ErrorRate<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
			double errorRate = function1.function(solution, test);

	    	str = String.valueOf(i);
	    	str += "," + errorRate;
	    	strs.add(str);
	    }
	    String fileName = Consts.EXPERIMENT_ID_DIR + sep + "results.csv";
	    Output.writeln(fileName, strs, false);*/

		//outputResults(nonDominatedSolutions, train,test);

        List<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> elite = algorithm.getelitePopulation();

		//Results of final generation
	    ArrayList<String> strs = new ArrayList<>();
	    String str = "pop,train,NR,ASWRL_tra,ASWRL_tst,RL,Cover,RW,test";
	    strs.add(str);

	    for(int i = 0; i < nonDominatedSolutions.size(); i++) {
            double errorRatetrain = nonDominatedSolutions.get(i).getObjective(0);
        	double ruleNum = nonDominatedSolutions.get(i).getObjective(1);
            double ASWRL_tra = nonDominatedSolutions.get(i).getObjective(2);
            AverageSingleWinnerRuleLength<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ASWRLfunc = new AverageSingleWinnerRuleLength<>();
            double ASWRL_tst = ASWRLfunc.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) nonDominatedSolutions.get(i), test);
            int ndim = test.getNdim();
            ASWRL_tst = (ndim > 0) ? (ASWRL_tst / (double)ndim) : 0.0;
            RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFunc = new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
            double TotalRuleLength = 0;
            for (int j = 0; j < nonDominatedSolutions.get(i).getNumberOfVariables(); j++) {
                 double RuleLength = RuleLengthFunc.function(nonDominatedSolutions.get(i).getVariable(j));
                 TotalRuleLength += RuleLength;
            }

            double TotalCover = 0;
            for (int j = 0; j < nonDominatedSolutions.get(i).getNumberOfVariables(); j++) {

            	 double Cover = 0;
            	 List<Double> support = new ArrayList<Double>();

            	 for (int k = 0; k < train.getNdim(); k++) {
            		  if (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) != 0) {

            			  if ((nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 1) ||
            				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 2) ||
            				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 4)){
            				   support.add(1.0);
            			  }else if ((nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 3) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 5) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 11) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 12) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 13)){
                				   support.add(1.0/2);
            			  }else if ((nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 6) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 9)){
                				   support.add(1.0/3);
            			  }else if ((nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 7) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 8)){
           				   support.add(2.0/3);
       			          }else if ((nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 10) ||
                				  (nonDominatedSolutions.get(i).getVariable(j).getVariable(k) == 14)){
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
            for (int j = 0; j < nonDominatedSolutions.get(i).getNumberOfVariables(); j++) {
                double RW = (Double) nonDominatedSolutions.get(i).getVariable(j).getRuleWeight().getRuleWeightValue();
                TotalRW += RW;
            }
            double AveRW = TotalRW/(nonDominatedSolutions.get(i).getNumberOfVariables());


            ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> function1
			= new ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
		    double errorRatetest = function1.function(nonDominatedSolutions.get(i), test);

	    	str = String.valueOf(i);
	    	str += "," + errorRatetrain;
	    	str += "," + ruleNum;
	    	str += "," + ASWRL_tra;
	    	str += "," + ASWRL_tst;
	    	str += "," + TotalRuleLength;
	    	str += "," + TotalCover;
	    	str += "," + AveRW;
	    	str += "," + errorRatetest;
	    	strs.add(str);
	    }
	    String fileName = Consts.EXPERIMENT_ID_DIR + sep + "results.csv";
	    Output.writeln(fileName, strs, false);

	    //Results of archive population
	    ArrayList<String> strsARC = new ArrayList<>();
	    String strARC = "pop,train,NR,ASWRL_tra,ASWRL_tst,RL,Cover,RW,test";
	    strsARC.add(strARC);

	    for(int i = 0; i < nonDominatedSolutionsARC.size(); i++) {
            double errorRatetrainARC = nonDominatedSolutionsARC.get(i).getObjective(0);
        	double ruleNumARC = nonDominatedSolutionsARC.get(i).getObjective(1);
            double ASWRL_traARC = nonDominatedSolutionsARC.get(i).getObjective(2);
            AverageSingleWinnerRuleLength<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ASWRLfuncARC = new AverageSingleWinnerRuleLength<>();
            double ASWRL_tstARC = ASWRLfuncARC.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) nonDominatedSolutionsARC.get(i), test);
            int ndim = test.getNdim();
            ASWRL_tstARC = (ndim > 0) ? (ASWRL_tstARC / (double)ndim) : 0.0;
            RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFuncARC = new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
            double TotalRuleLengthARC = 0;
            for (int j = 0; j < nonDominatedSolutionsARC.get(i).getNumberOfVariables(); j++) {
                 double RuleLengthARC = RuleLengthFuncARC.function(nonDominatedSolutionsARC.get(i).getVariable(j));
                 TotalRuleLengthARC += RuleLengthARC;
            }

            double TotalCoverARC = 0;
            for (int j = 0; j < nonDominatedSolutionsARC.get(i).getNumberOfVariables(); j++) {

            	 double CoverARC = 0;
            	 List<Double> supportARC = new ArrayList<Double>();

            	 for (int k = 0; k < train.getNdim(); k++) {
            		  if (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) != 0) {

            			  if ((nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 1) ||
            				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 2) ||
            				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 4)){
            				   supportARC.add(1.0);
            			  }else if ((nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 3) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 5) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 11) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 12) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 13)){
                				   supportARC.add(1.0/2);
            			  }else if ((nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 6) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 9)){
                				   supportARC.add(1.0/3);
            			  }else if ((nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 7) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 8)){
           				   supportARC.add(2.0/3);
       			          }else if ((nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 10) ||
                				  (nonDominatedSolutionsARC.get(i).getVariable(j).getVariable(k) == 14)){
           				   supportARC.add(1.0/4);
       			          }
            		  }
            	 }
            	 if (!supportARC.isEmpty()) {
            	     CoverARC = supportARC.stream().reduce(1.0, (a, b) -> a * b);
                   	 TotalCoverARC += CoverARC;
                 }
            }

            double TotalRWARC = 0;
            for (int j = 0; j < nonDominatedSolutionsARC.get(i).getNumberOfVariables(); j++) {
                double RWARC = (Double) nonDominatedSolutionsARC.get(i).getVariable(j).getRuleWeight().getRuleWeightValue();
                TotalRWARC += RWARC;
            }
            double AveRWARC = TotalRWARC/(nonDominatedSolutionsARC.get(i).getNumberOfVariables());

            ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> function1ARC
			= new ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
		    double errorRatetestARC = function1ARC.function(nonDominatedSolutionsARC.get(i), test);

	    	strARC = String.valueOf(i);
	    	strARC += "," + errorRatetrainARC;
	    	strARC += "," + ruleNumARC;
	    	strARC += "," + ASWRL_traARC;
	    	strARC += "," + ASWRL_tstARC;
	    	strARC += "," + TotalRuleLengthARC;
	    	strARC += "," + TotalCoverARC;
	    	strARC += "," + AveRWARC;
	    	strARC += "," + errorRatetestARC;
	    	strsARC.add(strARC);
	    }
	    String fileNameARC = Consts.EXPERIMENT_ID_DIR + sep + "resultsARC.csv";
	    Output.writeln(fileNameARC, strsARC, false);

	    //Results of elite population
	    ArrayList<String> strselite = new ArrayList<>();
	    String strelite = "pop,train,NR,ASWRL_tra,ASWRL_tst,RL,Cover,RW,test";
	    strselite.add(strelite);

	    for(int i = 0; i < elite.size(); i++) {
            double errorRatetrainelite = elite.get(i).getObjective(0);
            double ruleNumelite = elite.get(i).getObjective(1);
            double ASWRL_traelite = elite.get(i).getObjective(2);
            AverageSingleWinnerRuleLength<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ASWRLfuncelite = new AverageSingleWinnerRuleLength<>();
            double ASWRL_tstelite = ASWRLfuncelite.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) elite.get(i), test);
            int ndim = test.getNdim();
            ASWRL_tstelite = (ndim > 0) ? (ASWRL_tstelite / (double)ndim) : 0.0;
            RuleLength<MichiganSolution_Basic<Rule_Basic>> RuleLengthFuncelite= new RuleLength<MichiganSolution_Basic<Rule_Basic>>();
            double TotalRuleLengthelite = 0;
            for (int j = 0; j < elite.get(i).getNumberOfVariables(); j++) {
                 double RuleLengthelite = RuleLengthFuncelite.function(elite.get(i).getVariable(j));
                 TotalRuleLengthelite+= RuleLengthelite;
            }

            double TotalCoverelite = 0;
            for (int j = 0; j < elite.get(i).getNumberOfVariables(); j++) {

            	 double Coverelite = 0;
            	 List<Double> supportelite = new ArrayList<Double>();

            	 for (int k = 0; k < train.getNdim(); k++) {
            		  if (elite.get(i).getVariable(j).getVariable(k) != 0) {

            			  if ((elite.get(i).getVariable(j).getVariable(k) == 1) ||
            				  (elite.get(i).getVariable(j).getVariable(k) == 2) ||
            				  (elite.get(i).getVariable(j).getVariable(k) == 4)){
            				   supportelite.add(1.0);
            			  }else if ((elite.get(i).getVariable(j).getVariable(k) == 3) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 5) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 11) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 12) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 13)){
                				   supportelite.add(1.0/2);
            			  }else if ((elite.get(i).getVariable(j).getVariable(k) == 6) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 9)){
                				   supportelite.add(1.0/3);
            			  }else if ((elite.get(i).getVariable(j).getVariable(k) == 7) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 8)){
           				   supportelite.add(2.0/3);
       			          }else if ((elite.get(i).getVariable(j).getVariable(k) == 10) ||
                				  (elite.get(i).getVariable(j).getVariable(k) == 14)){
           				   supportelite.add(1.0/4);
       			          }
            		  }
            	 }
            	 if (!supportelite.isEmpty()) {
            	     Coverelite = supportelite.stream().reduce(1.0, (a, b) -> a * b);
                   	 TotalCoverelite += Coverelite;
                 }
            }

            double TotalRWelite = 0;
            for (int j = 0; j < elite.get(i).getNumberOfVariables(); j++) {
                double RWelite = (Double) elite.get(i).getVariable(j).getRuleWeight().getRuleWeightValue();
                TotalRWelite += RWelite;
            }
            double AveRWelite = TotalRWelite/(elite.get(i).getNumberOfVariables());

            ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> function1elite
			= new ErrorRateNoSideEffect<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>>();
		    double errorRatetestelite= function1elite.function(elite.get(i), test);

	    	strelite = String.valueOf(i);
	    	strelite += "," + errorRatetrainelite;
	    	strelite += "," + ruleNumelite;
	    	strelite += "," + ASWRL_traelite;
	    	strelite += "," + ASWRL_tstelite;
	    	strelite += "," + TotalRuleLengthelite;
	    	strelite += "," + TotalCoverelite;
	    	strelite += "," + AveRWelite;
	    	strelite += "," + errorRatetestelite;
	    	strselite.add(strelite);
	    }
	    String fileNameelite = Consts.EXPERIMENT_ID_DIR + sep + "resultselite.csv";
	    Output.writeln(fileNameelite, strselite, false);

		return;
	}
}
