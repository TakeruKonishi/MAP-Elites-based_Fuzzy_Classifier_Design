package cilabo.fuzzy.rule.antecedent.factory.impl;

import cilabo.data.DataSet;
import cilabo.data.pattern.Pattern;
import cilabo.fuzzy.knowledge.Knowledge;
import cilabo.fuzzy.rule.antecedent.factory.AntecedentIndexFactory;
import cilabo.main.Consts;
import cilabo.utility.GeneralFunctions;
import cilabo.utility.Random;

/**ヒューリスティックに基づいた前件部のFactory
 * @author Takigawa Hiroki
 */
public final class HeuristicRuleGenerationMethod implements AntecedentIndexFactory{

	/**  学習用データ*/
	private DataSet<?> train;
	/** 次元数 */
	private int dimension;
	/** データセットのパターン数 */
	private int dataSize;

	/**コンストラクタ
	 * @param train 生成時の学習に用いる学習用データ
	 */
	public HeuristicRuleGenerationMethod(DataSet<?> train) {
		this.train = train;
		this.dimension = Knowledge.getInstance().getNumberOfDimension();
		this.dataSize = train.getDataSize();
	}

	/** 前件部のファジィ集合のidを決定する
	 * @param index 前件部の生成時，学習に用いられるパターンのindex
	 * @return 決定された前件部のファジィセットのインデックス配列
	 */
	public int[] selectAntecedentPart(int index) {
		Pattern<?> pattern = train.getPattern(index);
		return this.calculateAntecedentPart(pattern);
	}

	/** 前件部のファジィ集合のidを決定する
	 * @param pattern 前件部の生成時，学習に用いられるパターン
	 * @return 決定された前件部のファジィセットのインデックス配列
	 */
	public int[] calculateAntecedentPart(Pattern<?> pattern) {
		double[] vector = pattern.getAttributeVector().getAttributeArray();

		//Don't careの決定
		double dcRate;
		if(Consts.IS_PROBABILITY_DONT_CARE) {
			//dcRate = Consts.DONT_CARE_RT;
			dcRate = (dimension - 3) / (double)dimension;
		}
		else {
			// (Ndim - const) / Ndim
			//dcRate = Math.max((dimension - Consts.ANTECEDENT_NUMBER_DO_NOT_DONT_CARE) / (double)dimension, Consts.DONT_CARE_RT);
			dcRate = (dimension - 3) / (double)dimension;
		}

		int[] antecedentIndex = new int[dimension];
		for(int n = 0; n < dimension; n++) {
			// don't care
			if(Random.getInstance().getGEN().nextBoolean(dcRate)) {
				antecedentIndex[n] = 0;
				continue;
			}

			// Categorical Judge
			if(vector[n] < 0) {
				antecedentIndex[n] = (int)vector[n];
				continue;
			}

			// Numerical
			int fuzzySetNum = Knowledge.getInstance().getFuzzySetNum(n)-1; //dontCare以外のファジィ集合の総数
			if(fuzzySetNum < 1) { antecedentIndex[n] = 0; continue; } //Dont care以外のファジィ集合が存在しない場合はDonat careに固定

			double[] membershipValueRoulette = new double[fuzzySetNum];
			double sumMembershipValue = 0;
			membershipValueRoulette[0] = 0;
			for(int h = 0; h < fuzzySetNum; h++) {
				sumMembershipValue += Knowledge.getInstance().getMembershipValue(vector[n], n, h+1);
				membershipValueRoulette[h] = sumMembershipValue;
			}

			double arrow = Random.getInstance().getGEN().nextDouble() * sumMembershipValue;
			for(int h = 0; h < fuzzySetNum; h++) {
				if(arrow < membershipValueRoulette[h]) {
					antecedentIndex[n] = h+1;
					break;
				}
			}
		}
		return antecedentIndex;
	}

	@Override
	public int[] create() {
		int patternIndex = Random.getInstance().getGEN().nextInt(this.dataSize);

		int[] antecedentIndex = this.selectAntecedentPart(patternIndex);

		return antecedentIndex;
	}

	@Override
	public int[][] create(int numberOfGenerateRule) {

		int[][] antecedentIndexArray = new int[numberOfGenerateRule][this.dimension];
		Integer[] antecedentIndexArrayIndex;

		//学習に用いるパターンのインデックス配列を生成する
		if(numberOfGenerateRule <= this.dataSize) {
			antecedentIndexArrayIndex = GeneralFunctions.samplingWithout(this.dataSize, numberOfGenerateRule, Random.getInstance().getGEN());
		}else {
			antecedentIndexArrayIndex = new Integer[numberOfGenerateRule];

			//"未定の学習用パターン数 < データセットのパターン数" になるまでデータセットの全パターンで埋める
			for(int i=0; i<numberOfGenerateRule/this.dataSize; i++) {
				for(int j=0; j<this.dataSize; j++) {
					antecedentIndexArrayIndex[i*this.dataSize + j] = j;
				}
			}
			//残りの未定の学習用パターンをランダムに選択する．
			int restArrayIndex = numberOfGenerateRule % this.dataSize;
			Integer[] buf = GeneralFunctions.samplingWithout(this.dataSize,
					restArrayIndex, Random.getInstance().getGEN());
			for(int i=0; i<buf.length; i++) {
				antecedentIndexArrayIndex[numberOfGenerateRule - i -1]= buf[i];
			}
		}

		//決定された学習元パターンから前件部を決定する
		for(int i=0; i<numberOfGenerateRule; i++) {
			antecedentIndexArray[i] = this.selectAntecedentPart(antecedentIndexArrayIndex[i]);
		}
		return antecedentIndexArray;
	}

	@Override
	public HeuristicRuleGenerationMethod copy() {
		return new HeuristicRuleGenerationMethod(this.train);
	}
}
