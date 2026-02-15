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

import cilabo.data.DataSet;
import cilabo.fuzzy.rule.impl.Rule_Basic;
import cilabo.gbml.component.variation.CrossoverAndMutationAndPittsburghLearningVariation;
import cilabo.gbml.objectivefunction.pittsburgh.AverageSingleWinnerRuleLength;
import cilabo.gbml.objectivefunction.pittsburgh.NumberOfRules;
import cilabo.gbml.problem.pittsburghFGBML_Problem.AbstractPittsburghFGBML;
import cilabo.gbml.solution.michiganSolution.impl.MichiganSolution_Basic;
import cilabo.gbml.solution.pittsburghSolution.PittsburghSolution;
import cilabo.gbml.solution.pittsburghSolution.impl.PittsburghSolution_Basic;
import cilabo.main.Consts;
import cilabo.util.fileoutput.PittsburghSolutionListOutput;

public class MEFCIII <S extends PittsburghSolution<?>>
	extends AbstractEvolutionaryAlgorithm<S, List<S>>
	implements ObservableEntity {

	private DataSet<?> train;

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

	private final NumberOfRules<S> ruleNumfunc = new NumberOfRules<>();
	private final AverageSingleWinnerRuleLength<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ASWRLfunc = new AverageSingleWinnerRuleLength<>();

	private static final int BINS = 50;
	private static final int MAX_GRID_INDEX = BINS - 1;
	private static final double GLOBAL_GRID_WIDTH = 1.0;
	private static final double LOCAL_GRID_WIDTH = 1.0 / BINS;

	// ===== セル統計（ログ用）=====
	private static class CellStat {
	  int n;       // 親として使われた回数
	  double mean; // 平均報酬
	}
	private final Map<Pair<Integer,Integer>, CellStat> cellStats = new LinkedHashMap<>();
	private final Map<Pair<Integer,Integer>, Integer> eliteUpdateCounts = new LinkedHashMap<>();
	private Pair<Integer,Integer> lastKey1 = null;
	private Pair<Integer,Integer> lastKey2 = null;
	// ============================

	private CellStat statOf(Pair<Integer,Integer> key){
		  return cellStats.computeIfAbsent(key, k -> new CellStat());
		}

	private void registerPull(Pair<Integer,Integer> key){
		 if (key == null) return;
		 statOf(key).n += 1;
	}

	private void applyReward(Pair<Integer,Integer> key, double r){
		 if (key == null) return;
		 CellStat st = statOf(key);
		 if (st.n <= 0) return; // 念のため
		 st.mean += (r - st.mean) / st.n;
	}


	/** Constructor */
	public MEFCIII(
			/* Arguments */
			DataSet<?> train,
			Problem<S> problem,
			int populationSize,
			int offspringPopulationSize,
			int frequency,
			String outputRootDir,
			CrossoverOperator<S> crossoverOperator,
			MutationOperator<S> mutationOperator,
			Termination termination) {
		/* Constructor Body */
		this.train = train;
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
		this.observable = new DefaultObservable<>("MEFCIII");

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

		/* Step 1. 初期個体群生成 - Initialization Population */
		population = createInitialPopulation();
		/* Step 2. 初期個体群評価 - Initial Population Evaluation */
		population = evaluatePopulation(population);
		/* 未勝利個体削除*/
		population = removeNoWinnerMichiganSolution(population);

		// 初期個体群をマッピングしてエリート選択
        updateEliteMap(population, eliteMap, GLOBAL_GRID_WIDTH, LOCAL_GRID_WIDTH);

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

			// エリート個体のみを含むリストを更新
	        population = new ArrayList<>(eliteMap.values());
	        // 親個体選択
	        matingPopulation = selectMatingPopulation(population, GLOBAL_GRID_WIDTH, LOCAL_GRID_WIDTH);
			/* 子個体群生成 - Offspring Generation */
            offspringPopulation = reproduction(matingPopulation);

            boolean usedP2 = false;
            Object attr = offspringPopulation.get(0).getAttribute("USED_PARENT2");
            if (!(attr instanceof Boolean)) {
              throw new IllegalStateException("USED_PARENT2 is missing on offspring after reproduction. attr=" + attr);
            }
            usedP2 = (Boolean) attr;

			/* 子個体群評価 - Offspring Evaluation */
            offspringPopulation = evaluatePopulation(offspringPopulation);
			/* 未勝利個体削除*/
            offspringPopulation = removeNoWinnerMichiganSolution(offspringPopulation);

            // アーカイブ更新（更新があれば true）
            boolean improved = updateEliteMap(offspringPopulation, eliteMap, GLOBAL_GRID_WIDTH, LOCAL_GRID_WIDTH);

           //親回数の加算（親1は必ず、親2は usedP2 のときだけ）
           registerPull(lastKey1);
           if (usedP2 && lastKey2 != null && !lastKey2.equals(lastKey1)) {
             registerPull(lastKey2);
           }

           //平均報酬（2値報酬：更新あり=1, なし=0）
           double r = improved ? 1.0 : 0.0;
           applyReward(lastKey1, r);
           if (usedP2 && lastKey2 != null && !lastKey2.equals(lastKey1)) {
             applyReward(lastKey2, r);
           }

            // エリート個体のみを含むリストを更新
            population = new ArrayList<>(eliteMap.values());

			/* JMetal progress update */
			updateProgress();
		}

		writeCellStatsCsv(eliteMap);

		/* ===  END  === */
		totalComputingTime = System.currentTimeMillis() - startTime;
	}

	// エリートマップを更新するメソッド
    private boolean updateEliteMap(List<S> solutions, Map<Pair<Integer, Integer>, S> eliteMap, double globalGridWidth, double localGridWidth) {
    	boolean changed = false;
        for (S solution : solutions) {
            double ruleNum = ruleNumfunc.function(solution);
            double ASWRL = ASWRLfunc.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) solution, train);
            int ndim = train.getNdim();
            double normASWRL = (ndim > 0) ? (ASWRL / (double)ndim) : 0.0;

            int globalIndex = (int)Math.floor((ruleNum - 1.0) / globalGridWidth);
            globalIndex = Math.max(0, Math.min(MAX_GRID_INDEX, globalIndex));
            int localIndex = (int)Math.floor(normASWRL / localGridWidth);
            localIndex = Math.max(0, Math.min(MAX_GRID_INDEX, localIndex));

            Pair<Integer, Integer> key = Pair.of(globalIndex, localIndex);

            // まだ存在しないキーは統計用マップにも登録
            cellStats.computeIfAbsent(key, k -> new CellStat());
            eliteUpdateCounts.putIfAbsent(key, 0);

            S prev = eliteMap.get(key);
            if (prev == null || solution.getObjective(0) < prev.getObjective(0)) {
              eliteMap.put(key, (S) solution.copy());
              eliteUpdateCounts.merge(key, 1, Integer::sum);
              changed = true;
            }
        }
        return changed;
    }

    private List<S> selectMatingPopulation(List<S> population, double globalGridWidth, double localGridWidth) {
        List<S> matingPool = new ArrayList<>();
        BoundedRandomGenerator<Integer> randomGenerator = (a, b) -> JMetalRandom.getInstance().nextInt(a, b);

        int parentIndex1 = randomGenerator.getRandomValue(0, population.size() - 1);
        S parent1 = population.get(parentIndex1);
        matingPool.add(parent1);

        // 常にランダム交叉
        int parentIndex2;
        do {
            parentIndex2 = randomGenerator.getRandomValue(0, population.size() - 1);
        } while (parentIndex1 == parentIndex2);
        S parent2 = population.get(parentIndex2);
        matingPool.add(parent2);

        // 近傍のセルから2つ目の親を選択
        /*Pair<Integer, Integer> parent1Key = getGridKey(parent1, globalGridWidth, localGridWidth);
        List<S> neighbors = getNeighborSolutions(parent1Key, population, globalGridWidth, localGridWidth);

        if (!neighbors.isEmpty() && JMetalRandom.getInstance().nextDouble() < 0.5) {
            // 近傍交叉
            S parent2 = neighbors.get(randomGenerator.getRandomValue(0, neighbors.size() - 1));
            matingPool.add(parent2);
        } else {
            // ランダム交叉
            int parentIndex2;
            do {
                parentIndex2 = randomGenerator.getRandomValue(0, population.size() - 1);
            } while (parentIndex1 == parentIndex2);
            S parent2 = population.get(parentIndex2);
            matingPool.add(parent2);
        }*/

        //親セルキーを記録（後でpull/reward更新に使う）
        lastKey1 = getGridKey(parent1, globalGridWidth, localGridWidth);
        lastKey2 = getGridKey(parent2, globalGridWidth, localGridWidth);

        return matingPool;
    }

    // 近傍の解を取得
    private List<S> getNeighborSolutions(Pair<Integer, Integer> key, List<S> population, double globalGridWidth, double localGridWidth) {
        List<S> neighbors = new ArrayList<>();
        for (S solution : population) {
            Pair<Integer, Integer> solutionKey = getGridKey(solution, globalGridWidth, localGridWidth);
            if (Math.abs(solutionKey.getLeft() - key.getLeft()) <= 5 &&
                Math.abs(solutionKey.getRight() - key.getRight()) <= 5) {
            	neighbors.add(solution);
            }
        }
        return neighbors;
    }

    // 解のグリッドキーを取得
    private Pair<Integer, Integer> getGridKey(S solution, double globalGridWidth, double localGridWidth) {
    	double ruleNum = ruleNumfunc.function(solution);
        double ASWRL = ASWRLfunc.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) solution, train);
        int ndim = train.getNdim();
        double normASWRL = (ndim > 0) ? (ASWRL / (double)ndim) : 0.0;

        int globalIndex = (int)Math.floor((ruleNum - 1.0) / globalGridWidth);
        globalIndex = Math.max(0, Math.min(MAX_GRID_INDEX, globalIndex));
        int localIndex = (int)Math.floor(normASWRL / localGridWidth);
        localIndex = Math.max(0, Math.min(MAX_GRID_INDEX, localIndex));

        return Pair.of(globalIndex, localIndex);
    }

    private void writeCellStatsCsv(Map<Pair<Integer,Integer>, S> eliteMap) {
    	  String sep = File.separator;
    	  String outPath = outputRootDir + sep + "cell_stats.csv";

    	  List<Pair<Integer,Integer>> keys = new ArrayList<>(eliteMap.keySet());
    	  keys.sort((a,b) -> {
    	    int c1 = Integer.compare(a.getLeft(), b.getLeft());
    	    if (c1 != 0) return c1;
    	    return Integer.compare(a.getRight(), b.getRight());
    	  });

    	  try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File(outPath))) {
    	    pw.println("globalIndex,localIndex,parentPulls,meanReward,eliteUpdates");

    	    for (Pair<Integer,Integer> key : keys) {
    	      CellStat st = cellStats.get(key);
    	      int pulls = (st == null) ? 0 : st.n;
    	      double mean = (st == null) ? 0.0 : st.mean;
    	      int updates = eliteUpdateCounts.getOrDefault(key, 0);

    	      pw.printf(java.util.Locale.US, "%d,%d,%d,%.8f,%d%n",
    	          key.getLeft(), key.getRight(), pulls, mean, updates);
    	    }
    	  } catch (java.io.FileNotFoundException e) {
    	    throw new RuntimeException("Failed to write cell_stats.csv: " + outPath, e);
    	  }
    }

    //===== QD推移出力 =====
    private void appendQdProgressCsv(int evaluations, boolean overwrite) {
     String sep = File.separator;
     String outPath = outputRootDir + sep + "qd_progress.csv";
     java.io.File file = new java.io.File(outPath);

     // overwrite=true のときは新規作成（上書き）してヘッダを書く
     boolean append = !overwrite;

     List<S> elites = this.getPopulation();
     int totalCells = BINS * BINS;

     int coverageCells = (elites == null) ? 0 : elites.size();
     double qdScore = 0.0;
     double maxFitness = Double.NEGATIVE_INFINITY;

     if (elites != null && !elites.isEmpty()) {
  	    for (S s : elites) {
  	      if (s == null) continue; // 念のため（基本は入らない前提）

  	      double obj0 = s.getObjective(0);
  	      double fitness = 1.0 - obj0;

  	      qdScore += fitness;
  	      if (fitness > maxFitness) {
  	        maxFitness = fitness;
  	      }
  	    }
     }

     if (maxFitness == Double.NEGATIVE_INFINITY) {
  	    maxFitness = 0.0; // エリートが空 or 全部nullの異常系
     }

     double coverageRatio = (totalCells > 0) ? (coverageCells / (double) totalCells) : 0.0;

     try (java.io.PrintWriter pw =
         new java.io.PrintWriter(new java.io.FileOutputStream(file, append))) {

       if (overwrite) {
      	 pw.println("evaluations,coverageCells,coverageRatio,qdScore,maxFitness");
       }

       pw.printf(java.util.Locale.US, "%d,%d,%.8f,%.8f,%.8f%n",
      	        evaluations, coverageCells, coverageRatio, qdScore, maxFitness);

     } catch (java.io.IOException e) {
       throw new RuntimeException("Failed to write qd_progress.csv: " + outPath, e);
     }
    }
    //======================


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

	        appendQdProgressCsv(evaluations, true);
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

    	        appendQdProgressCsv(evaluations, false);

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

	    		appendQdProgressCsv(evaluations, false);
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
		return "MEFCIII";
	}

	@Override
	public String getDescription() {
		return "MEFCIII";
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
