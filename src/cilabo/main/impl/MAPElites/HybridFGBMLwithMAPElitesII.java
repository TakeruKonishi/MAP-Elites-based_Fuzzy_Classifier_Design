package cilabo.main.impl.MAPElites;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.uma.jmetal.algorithm.impl.AbstractEvolutionaryAlgorithm;
import org.uma.jmetal.component.evaluation.Evaluation;
import org.uma.jmetal.component.evaluation.impl.SequentialEvaluation;
import org.uma.jmetal.component.initialsolutioncreation.InitialSolutionsCreation;
import org.uma.jmetal.component.initialsolutioncreation.impl.RandomSolutionsCreation;
import org.uma.jmetal.component.replacement.Replacement;
import org.uma.jmetal.component.selection.MatingPoolSelection;
import org.uma.jmetal.component.termination.Termination;
import org.uma.jmetal.component.variation.Variation;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.util.JMetalLogger;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.fileoutput.impl.DefaultFileOutputContext;
import org.uma.jmetal.util.observable.Observable;
import org.uma.jmetal.util.observable.ObservableEntity;
import org.uma.jmetal.util.observable.impl.DefaultObservable;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import cilabo.fuzzy.rule.impl.Rule_Basic;
import cilabo.gbml.component.variation.CrossoverAndMutationAndPittsburghLearningVariation;
import cilabo.gbml.objectivefunction.michigan.RuleLength;
import cilabo.gbml.objectivefunction.pittsburgh.NumberOfRules;
import cilabo.gbml.problem.pittsburghFGBML_Problem.AbstractPittsburghFGBML;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;
import cilabo.main.Consts;
import cilabo.util.fileoutput.PittsburghSolutionListOutput;

//セルの粗さ調整＋近傍個体での交叉の実装
public class HybridFGBMLwithMAPElitesII <S extends PittsburghSolution<?>>
	extends AbstractEvolutionaryAlgorithm<S, List<S>>
	implements ObservableEntity {

	private int evaluations;
	private int populationSize;
	private int offspringPopulationSize;
	private int frequency;
	private String outputRootDir;

	protected SelectionOperator<List<S>, S> selectionOperator;
	protected CrossoverOperator<S> crossoverOperator;
	protected MutationOperator<S> mutationOperator;
	private Termination termination;
	private Variation<S> variation;
	private InitialSolutionsCreation<S> initialSolutionsCreation;

	private Map<String, Object> algorithmStatusData;

	private Evaluation<S> evaluation;
	private Replacement<S> replacement;
	private MatingPoolSelection<S> selection;

	private long startTime;
	private long totalComputingTime;

	private Observable<Map<String, Object>> observable;

	// 既存のフィールドと初期化コードに加え、新しいフィールドを追加
    private boolean isFineGrainedPhase; // 現在のセル分割状態を示す
    private int coarseGridWidth; // 初期段階での粗いセル幅
    private int fineGridWidth; // 後半段階での細かいセル幅
    private int generationThreshold; // セル切り替え世代数の閾値

	/** Constructor */
	public HybridFGBMLwithMAPElitesII(
			/* Arguments */
			Problem<S> problem,
			int populationSize,
			int offspringPopulationSize,
			int frequency,
			String outputRootDir,
			CrossoverOperator<S> crossoverOperator,
			MutationOperator<S> mutationOperator,
			Termination termination) {
		/* Constructor Body */
		this.problem = problem;

		this.populationSize = populationSize;
		this.offspringPopulationSize = offspringPopulationSize;
		this.frequency = frequency;
		this.outputRootDir = outputRootDir;

		this.crossoverOperator = crossoverOperator;
		this.mutationOperator = mutationOperator;
		this.termination = termination;

		/* MAP-Elites */

		this.variation =
				new CrossoverAndMutationAndPittsburghLearningVariation<S>(
						offspringPopulationSize, crossoverOperator, mutationOperator);

		this.initialSolutionsCreation = new RandomSolutionsCreation<S>(problem, populationSize);

		this.evaluation = new SequentialEvaluation<>();

		this.algorithmStatusData = new HashMap<>();
		this.observable = new DefaultObservable<>("Hybrid FGBML with MAP-Elites algorithm");

		this.isFineGrainedPhase = false;
		this.coarseGridWidth = 2;
		this.fineGridWidth = 1;
	    this.generationThreshold = Consts.TERMINATE_EVALUATION/2;

	}

	@Override
	public void run() {
		startTime = System.currentTimeMillis();

		JMetalRandom.getInstance().setSeed(Consts.RAND_SEED);

		/* === START === */
		List<S> offspringPopulation;
		List<S> matingPopulation;

        //各セルのエリート個体を保持するためのマップ
        Map<Pair<Integer,Integer>, S> eliteMap = new LinkedHashMap<>();

        int currentGridWidth = coarseGridWidth; // 初期は粗いセル

		/* Step 1. 初期個体群生成 - Initialization Population */
		population = createInitialPopulation();
		/* Step 2. 初期個体群評価 - Initial Population Evaluation */
		population = evaluatePopulation(population);
		/* 未勝利個体削除*/
		population = removeNoWinnerMichiganSolution(population);

		// 初期個体群をマッピングしてエリート選択
        updateEliteMap(population, eliteMap, currentGridWidth);

        // エリート個体のみを含むリストを更新
        population = new ArrayList<>(eliteMap.values());

		/* JMetal progress initialization */
		initProgress();

		/*Element population_ = XML_manager.getInstance().createElement(XML_TagName.population);
		for(S solution: this.getResult()) {
			XML_manager.getInstance().addElement(population_, solution.toElement());
		}
		Element generations_ = XML_manager.getInstance().createElement(XML_TagName.generations, XML_TagName.evaluation, String.valueOf(0));
		//knowledge出力用
		XML_manager.getInstance().addElement(generations_, Knowledge.getInstance().toElement());
		XML_manager.getInstance().addElement(generations_, population_);
    	XML_manager.getInstance().addElement(XML_manager.getInstance().getRoot(), generations_);*/

		/* GA loop */
		while(!isStoppingConditionReached()) {

            if (evaluations >= generationThreshold && !isFineGrainedPhase) {
                // 細かいセルに切り替え
                isFineGrainedPhase = true;
                currentGridWidth = fineGridWidth;
                eliteMap.clear(); // 古いエリートをクリア
                updateEliteMap(population, eliteMap, currentGridWidth); // 新しい粒度でリマップ
            }

			population = new ArrayList<>(eliteMap.values());
            matingPopulation = selectMatingPopulation(population, currentGridWidth);
            offspringPopulation = reproduction(matingPopulation);
            offspringPopulation = evaluatePopulation(offspringPopulation);
            offspringPopulation = removeNoWinnerMichiganSolution(offspringPopulation);

            updateEliteMap(offspringPopulation, eliteMap, currentGridWidth);

            // エリート個体のみを含むリストを更新
            population = new ArrayList<>(eliteMap.values());

			/* JMetal progress update */
			updateProgress();
		}

		/* ===  END  === */
		totalComputingTime = System.currentTimeMillis() - startTime;
	}

	// エリートマップを更新するメソッド
    private void updateEliteMap(List<S> solutions, Map<Pair<Integer, Integer>, S> eliteMap, int gridWidth) {
        for (S solution : solutions) {
            double ruleNum = new NumberOfRules<S>().function(solution);
            double totalRuleLength = 0;
            for (int i = 0; i < solution.getNumberOfVariables(); i++) {
                totalRuleLength += new RuleLength<MichiganSolution_Basic<Rule_Basic>>().function(
                    (MichiganSolution_Basic<Rule_Basic>) solution.getVariable(i));
            }

            int ruleNumIndex;
            int ruleLengthIndex;

            if (gridWidth == fineGridWidth) {
                // 細かいセルの場合は元の切り捨てに戻す
            	ruleNumIndex = (int)(ruleNum/gridWidth);
                ruleLengthIndex = (int)(totalRuleLength/gridWidth);
            } else {
                ruleNumIndex = Math.min((int)(ruleNum/gridWidth), (int)(Consts.MAX_RULE_NUM / gridWidth) - 1);
                ruleLengthIndex = (int)(totalRuleLength/gridWidth);
            }

            Pair<Integer, Integer> key = Pair.of(ruleNumIndex, ruleLengthIndex);

            eliteMap.compute(key, (k, existingSolution) -> {
                if (existingSolution == null || solution.getObjective(0) < existingSolution.getObjective(0)) {
                    return (S) solution.copy();
                } else {
                    return existingSolution;
                }
            });
        }
    }

    // 近傍交叉とランダム交叉を適用した選択メソッド
    private List<S> selectMatingPopulation(List<S> population, int gridWidth) {
        List<S> matingPool = new ArrayList<>();
        BoundedRandomGenerator<Integer> randomGenerator = (a, b) -> JMetalRandom.getInstance().nextInt(a, b);

        int parentIndex1 = randomGenerator.getRandomValue(0, population.size() - 1);
        S parent1 = population.get(parentIndex1);
        matingPool.add(parent1);

        // 常にランダム交叉
        /*int parentIndex2;
        do {
            parentIndex2 = randomGenerator.getRandomValue(0, population.size() - 1);
        } while (parentIndex1 == parentIndex2);
        matingPool.add(population.get(parentIndex2));*/

        // 近傍のセルから2つ目の親を選択
        Pair<Integer, Integer> parent1Key = getGridKey(parent1, gridWidth);
        List<S> neighbors = getNeighborSolutions(parent1Key, population, gridWidth);

        if (!neighbors.isEmpty() && isFineGrainedPhase && JMetalRandom.getInstance().nextDouble() < 0.75) {
            // 近傍交叉
            S parent2 = neighbors.get(randomGenerator.getRandomValue(0, neighbors.size() - 1));
            matingPool.add(parent2);
        } else {
            // ランダム交叉
            int parentIndex2;
            do {
                parentIndex2 = randomGenerator.getRandomValue(0, population.size() - 1);
            } while (parentIndex1 == parentIndex2);
            matingPool.add(population.get(parentIndex2));
        }

        return matingPool;
    }

    // 近傍の解を取得
    private List<S> getNeighborSolutions(Pair<Integer, Integer> key, List<S> population, int gridWidth) {
        List<S> neighbors = new ArrayList<>();
        for (S solution : population) {
            Pair<Integer, Integer> solutionKey = getGridKey(solution, gridWidth);
            if (Math.abs(solutionKey.getLeft() - key.getLeft()) <= 5 &&
                Math.abs(solutionKey.getRight() - key.getRight()) <= 5) {
                neighbors.add(solution);
            }
        }
        return neighbors;
    }

    // 解のグリッドキーを取得
    private Pair<Integer, Integer> getGridKey(S solution, int gridWidth) {
        double ruleNum = new NumberOfRules<S>().function(solution);
        double totalRuleLength = 0;
        for (int i = 0; i < solution.getNumberOfVariables(); i++) {
            totalRuleLength += new RuleLength<MichiganSolution_Basic<Rule_Basic>>().function(
                (MichiganSolution_Basic<Rule_Basic>) solution.getVariable(i));
        }
        int ruleNumIndex;
        int ruleLengthIndex;

        if (gridWidth == fineGridWidth) {
            // 細かいセルの場合は元の切り捨てに戻す
        	ruleNumIndex = (int)(ruleNum/gridWidth);
            ruleLengthIndex = (int)(totalRuleLength/gridWidth);
        } else {
        	ruleNumIndex = Math.min((int)(ruleNum/gridWidth), (int)(Consts.MAX_RULE_NUM / gridWidth) - 1);
            ruleLengthIndex = (int)(totalRuleLength/gridWidth);
        }

        return Pair.of(ruleNumIndex, ruleLengthIndex);
    }

	@Override
	protected void initProgress() {
		evaluations = populationSize;

	    algorithmStatusData.put("EVALUATIONS", evaluations);
	    algorithmStatusData.put("POPULATION", population);
	    algorithmStatusData.put("COMPUTING_TIME", System.currentTimeMillis() - startTime);

	    observable.setChanged();
	    observable.notifyObservers(algorithmStatusData);

	    String sep = File.separator;
	    Integer evaluations = (Integer)algorithmStatusData.get("EVALUATIONS");

	    if(evaluations != null) {
	        new PittsburghSolutionListOutput((List<PittsburghSolution<?>>) this.getPopulation())
            .setVarFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("VAR-%d.csv", evaluations), ","))
            .setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUN-%d.csv", evaluations), ","))
            .print();
	    }
		else {
			JMetalLogger.logger.warning(getClass().getName()
			+ ": The algorithm has not registered yet any info related to the EVALUATIONS key");
		}
	}


	@Override
	protected void updateProgress() {
		evaluations += offspringPopulationSize;
	    algorithmStatusData.put("EVALUATIONS", evaluations);
	    algorithmStatusData.put("POPULATION", population);
	    algorithmStatusData.put("COMPUTING_TIME", System.currentTimeMillis() - startTime);

	    observable.setChanged();
	    observable.notifyObservers(algorithmStatusData);

	    String sep = File.separator;
	    Integer evaluations = (Integer)algorithmStatusData.get("EVALUATIONS");
	    if(evaluations != null) {
	    	if(evaluations * 10 % frequency == 0 && evaluations % frequency != 0) System.out.print(". ");
	    	if(evaluations % frequency == 0 && evaluations != Consts.TERMINATE_EVALUATION) {
	    		System.out.print(" ->");
	    		for(int i=0; i<getPopulation().get(0).getNumberOfObjectives(); i++) {
	    			double tmp=0;
	    			for(int j=0; j<getPopulation().size(); j++) {
	    				tmp += getPopulation().get(j).getObjective(i);
	    			}
	    			tmp /= getPopulation().size();
		    		System.out.print(String.format("objectives[%d]: %.8f.. ", i, tmp));
	    		}
	    		System.out.println(); System.out.println();

	    		/*出力された数値が0埋めで同じ桁数になるversion*/
	    	    /*new PittsburghSolutionListOutput((List<PittsburghSolution<?>>) this.getResult())
	            .setVarFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("VAR-%010d.csv", evaluations), ","))
	            .setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUN-%010d.csv", evaluations), ","))
	            .print();*/

	    		/*出力された数値が0埋めされないversion*/
    	        new PittsburghSolutionListOutput((List<PittsburghSolution<?>>) this.getPopulation())
                .setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUN-%d.csv", evaluations), ","))
                .printFunonly();

	    		/*Element population = XML_manager.getInstance().createElement(XML_TagName.population);

	    		for(S solution: this.getResult()) {
	    			Element pittsburghSolution = solution.toElement();
	    			XML_manager.getInstance().addElement(population, pittsburghSolution);
	    		}

	    		Element generations = XML_manager.getInstance().createElement(XML_TagName.generations, XML_TagName.evaluation, String.valueOf(evaluations));

	    		//knowlwdge出力用
	    		XML_manager.getInstance().addElement(generations, Knowledge.getInstance().toElement());
	    		XML_manager.getInstance().addElement(generations, population);
		    	XML_manager.getInstance().addElement(XML_manager.getInstance().getRoot(), generations);*/
	    	}
	    	if(evaluations == Consts.TERMINATE_EVALUATION) {
	    		System.out.print(" ->");
	    		for(int i=0; i<getPopulation().get(0).getNumberOfObjectives(); i++) {
	    			double tmp=0;
	    			for(int j=0; j<getPopulation().size(); j++) {
	    				tmp += getPopulation().get(j).getObjective(i);
	    			}
	    			tmp /= getPopulation().size();
		    		System.out.print(String.format("objectives[%d]: %.8f.. ", i, tmp));
	    		}
	    		System.out.println(); System.out.println();

	    		new PittsburghSolutionListOutput((List<PittsburghSolution<?>>) this.getPopulation())
                .setVarFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("VAR-%d.csv", evaluations), ","))
                .setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUN-%d.csv", evaluations), ","))
                .print();
	    	}
	    }
		else {
			JMetalLogger.logger.warning(getClass().getName()
			+ ": The algorithm has not registered yet any info related to the EVALUATIONS key");
		}
	}

	@Override
	protected boolean isStoppingConditionReached() {
		return termination.isMet(algorithmStatusData);
	}

	@Override
	protected List<S> createInitialPopulation() {
		return initialSolutionsCreation.create();
	}

	@Override
	protected List<S> evaluatePopulation(List<S> population) {
		return  evaluation.evaluate(population, getProblem());
	}

	@Override
	protected List<S> selection(List<S> population) {
		return this.selection.select(population);
	}

	@Override
	protected List<S> reproduction(List<S> matingPool){
		return variation.variate(population, matingPool);
	}

	@Override
	protected List<S> replacement(List<S> population, List<S> offspringPopulation) {
		return replacement.replace(population, offspringPopulation);
	}

	protected List<S> removeNoWinnerMichiganSolution(List<S> population) {
		/* 未勝利個体削除*/
	    IntStream.range(0, population.size())
	        .forEach(i -> ((AbstractPittsburghFGBML)problem).removeNoWinnerMichiganSolution(population.get(i)));
		return population;
	}

	@Override
	public List<S> getResult(){
		return SolutionListUtils.getNonDominatedSolutions(getPopulation());
	}

	@Override
	public String getName() {
		return "Hybrid-style FGBML with MAP-Elites";
	}

	@Override
	public String getDescription() {
		return "Hybrid-style Fuzzy Genetics-Based Machine Learning with MAP-Elites";
	}

	public Map<String, Object> getAlgorithmStatusData() {
		return algorithmStatusData;
	}

	@Override
	public Observable<Map<String, Object>> getObservable() {
		return observable;
	}

	public long getTotalComputingTime() {
		return totalComputingTime;
	}

	public long getEvaluations() {
		return evaluations;
	}
}
