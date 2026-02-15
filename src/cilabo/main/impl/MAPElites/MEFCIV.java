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

public class MEFCIV <S extends PittsburghSolution<?>>
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
  private final AverageSingleWinnerRuleLength<PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>> ASWRLfunc =
      new AverageSingleWinnerRuleLength<>();

  // ===== Bandit(UCB) 追加: 腕統計 =====
  private static class ArmStat {
    int n;          // 選択回数
    double mean;    // 報酬平均
    ArmStat(int n, double mean){ this.n = n; this.mean = mean; }
  }
  private final Map<Pair<Integer,Integer>, ArmStat> banditStats = new LinkedHashMap<>();
  private int totalPulls = 0;        // 全腕の総選択回数（親選択=1プルと数える）
  private final double ucbC = 1.0;   // UCB1 の探索係数 c
  private Pair<Integer,Integer> lastKey1 = null; // 直近に選んだ親1のセル
  private Pair<Integer,Integer> lastKey2 = null; // 直近に選んだ親2のセル
  // ================================

  /** Constructor */
  public MEFCIV(
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
    this.observable = new DefaultObservable<>("MEFCIV");
  }

  @Override
  public void run() {
    startTime = System.currentTimeMillis();
    JMetalRandom.getInstance().setSeed(Consts.RAND_SEED);

    /* === START === */
    List<S> offspringPopulation;
    List<S> matingPopulation;

    // 各セルのエリート個体を保持するためのマップ
    Map<Pair<Integer,Integer>, S> eliteMap = new LinkedHashMap<>();

    // グリッド幅の設定
    double globalGridWidth = 1;    // ルール数のグリッド幅
    double localGridWidth  = 0.2; // 平均単一勝利ルール長のグリッド幅

    /* Step 1. 初期個体群生成 - Initialization Population */
    population = createInitialPopulation();
    /* Step 2. 初期個体群評価 - Initial Population Evaluation */
    population = evaluatePopulation(population);
    /* 未勝利個体削除*/
    population = removeNoWinnerMichiganSolution(population);

    // 初期個体群をマッピングしてエリート選択
    updateEliteMap(population, eliteMap, globalGridWidth, localGridWidth);

    // バンディット腕の初期化（初回のみの安定化）
    updateBanditOnNewArchiveKeys(eliteMap, /*initializeBandit=*/true);

    // エリート個体のみ
    population = new ArrayList<>(eliteMap.values());

    /* JMetal progress initialization */
    initProgress();

    /* GA loop */
    while(!isStoppingConditionReached()) {

      // エリート個体のみ
      population = new ArrayList<>(eliteMap.values());

      // 親個体選択（UCB1×2）
      matingPopulation = selectMatingPopulation(population, globalGridWidth, localGridWidth, eliteMap);

      /* 子個体群生成 - Offspring Generation */
      offspringPopulation = reproduction(matingPopulation);
      /* 子個体群評価 - Offspring Evaluation */
      offspringPopulation = evaluatePopulation(offspringPopulation);
      /* 未勝利個体削除*/
      offspringPopulation = removeNoWinnerMichiganSolution(offspringPopulation);

      // アーカイブ更新（更新があれば true）
      boolean improved = updateEliteMap(offspringPopulation, eliteMap, globalGridWidth, localGridWidth);

      // ===== Bandit(UCB) 追加: 報酬更新（二値） =====
      double r = improved ? 1.0 : 0.0;
      applyBanditReward(lastKey1, r);
      applyBanditReward(lastKey2, r);
      // ===========================================

      // エリート個体のみ
      population = new ArrayList<>(eliteMap.values());

      /* JMetal progress update */
      updateProgress();
    }

    /* ===  END  === */
    totalComputingTime = System.currentTimeMillis() - startTime;
  }

  // ===== UCB1 のヘルパ =====
  private ArmStat statOf(Pair<Integer,Integer> key){
    // 楽観的初期化: n=1, mean=1.0（未試行セルを初期に選びやすく）
    return banditStats.computeIfAbsent(key, k -> new ArmStat(1, 1.0));
  }

  private double ucbScore(Pair<Integer,Integer> key){
    ArmStat st = statOf(key);
    // log 項の安定化（T<2 のときでも正に保つ）
    double T = Math.max(2.0, (double)Math.max(1, totalPulls));
    return st.mean + ucbC * Math.sqrt(Math.log(T) / st.n);
  }

  private void applyBanditReward(Pair<Integer,Integer> key, double r){
    if (key == null) return;
    ArmStat st = statOf(key);
    st.n += 1;
    st.mean += (r - st.mean) / st.n; // 逐次平均
    totalPulls += 1;
  }
  // ========================

  // アーカイブを更新し、変化があれば true を返す（戻り値追加）
  private boolean updateEliteMap(List<S> solutions, Map<Pair<Integer, Integer>, S> eliteMap,
                                 double globalGridWidth, double localGridWidth) {
    boolean changed = false;
    for (S solution : solutions) {
      double ruleNum = ruleNumfunc.function(solution);
      double ASWRL = ASWRLfunc.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) solution, train);

      int globalIndex = (int)(ruleNum/globalGridWidth);
      int localIndex  = (int)(ASWRL/localGridWidth);

      Pair<Integer, Integer> key = Pair.of(globalIndex, localIndex);

      S prev = eliteMap.get(key);
      if (prev == null || solution.getObjective(0) < prev.getObjective(0)) {
        eliteMap.put(key, (S) solution.copy());
        changed = true;
      }
    }
    // 新規セルが現れた場合はバンディット腕も用意（楽観的初期化）
    updateBanditOnNewArchiveKeys(eliteMap, /*initializeBandit=*/false);
    return changed;
  }

  // アーカイブに存在するキーに対応するバンディット腕を初期化（未登録のみ）
  private void updateBanditOnNewArchiveKeys(Map<Pair<Integer,Integer>, S> eliteMap, boolean initializeBandit){
    for (Pair<Integer,Integer> key : eliteMap.keySet()){
      if (!banditStats.containsKey(key)){
        banditStats.put(key, new ArmStat(1, 1.0)); // 楽観的初期化
      }
    }
    if (initializeBandit){
      // 初期化直後は totalPulls>=2 程度にしておくと UCB が安定（任意）
      totalPulls = Math.max(totalPulls, 2);
    }
  }

  // 既存シグネチャを拡張：eliteMapも受け取り、UCBで2親を選択
  private List<S> selectMatingPopulation(List<S> population,
                                         double globalGridWidth, double localGridWidth,
                                         Map<Pair<Integer,Integer>, S> eliteMap) {
    List<S> matingPool = new ArrayList<>(2);

    // 現在アーカイブに存在するセル群（= 腕集合）
    List<Pair<Integer,Integer>> keys = new ArrayList<>(eliteMap.keySet());
    if (keys.isEmpty()){
      // フォールバック（理論上ほぼ無い）：ランダム2親
      BoundedRandomGenerator<Integer> rnd = (a, b) -> JMetalRandom.getInstance().nextInt(a, b);
      int i1 = rnd.getRandomValue(0, population.size()-1);
      int i2 = rnd.getRandomValue(0, population.size()-1);
      while(i1 == i2) i2 = rnd.getRandomValue(0, population.size()-1);
      matingPool.add(population.get(i1));
      matingPool.add(population.get(i2));
      lastKey1 = lastKey2 = null;
      return matingPool;
    }

    // ---- 親1: UCB argmax ----
    Pair<Integer,Integer> bestKey1 = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (Pair<Integer,Integer> k : keys){
      double s = ucbScore(k);
      if (s > bestScore){
        bestScore = s; bestKey1 = k;
      }
    }
    S parent1 = eliteMap.get(bestKey1);
    matingPool.add(parent1);
    lastKey1 = bestKey1;

    // ---- 親2: 親1と同じセルは原則避け、UCB argmax（候補が無ければ同セルも可）----
    Pair<Integer,Integer> bestKey2 = null;
    bestScore = Double.NEGATIVE_INFINITY;
    for (Pair<Integer,Integer> k : keys){
      if (!k.equals(bestKey1)){
        double s = ucbScore(k);
        if (s > bestScore){
          bestScore = s; bestKey2 = k;
        }
      }
    }
    if (bestKey2 == null){
      // 1セルしか無い場合など
      bestKey2 = bestKey1;
    }
    S parent2 = eliteMap.get(bestKey2);
    matingPool.add(parent2);
    lastKey2 = bestKey2;

    return matingPool;
  }

  // 近傍の解を取得（未使用だが残置）
  private List<S> getNeighborSolutions(Pair<Integer, Integer> key, List<S> population,
                                       double globalGridWidth, double localGridWidth) {
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

  // 解のグリッドキー
  private Pair<Integer, Integer> getGridKey(S solution, double globalGridWidth, double localGridWidth) {
    double ruleNum = ruleNumfunc.function(solution);
    double ASWRL = ASWRLfunc.function((PittsburghSolution_Basic<MichiganSolution_Basic<Rule_Basic>>) solution, train);

    int globalIndex = (int)(ruleNum/globalGridWidth);
    int localIndex  = (int)(ASWRL/localGridWidth);
    return Pair.of(globalIndex, localIndex);
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

        new PittsburghSolutionListOutput((List<PittsburghSolution<?>>) this.getPopulation())
            .setFunFileOutputContext(new DefaultFileOutputContext(outputRootDir + sep + String.format("FUN-%d.csv", evaluations), ","))
            .printFunonly();
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
    return "MEFCIV";
  }

  @Override
  public String getDescription() {
    return "MEFCIV";
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
