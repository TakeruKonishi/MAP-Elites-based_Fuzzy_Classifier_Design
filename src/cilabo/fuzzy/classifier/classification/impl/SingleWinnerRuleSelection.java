package cilabo.fuzzy.classifier.classification.impl;

import java.util.List;

import cilabo.data.pattern.Pattern;
import cilabo.fuzzy.classifier.classification.Classification;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;

/** 単一勝利ルールの実装クラス
 * @author Takigawa Hiroki
 *
 * @param <michiganSolution> 識別器が扱うMichiganSolutionクラスの型
 */
public final class SingleWinnerRuleSelection <michiganSolution extends MichiganSolution<?>> implements Classification <michiganSolution> {

	/**
	 * 単一勝利ルールに基づいて勝利ルールを出力する．
	 * 適合度が最大値となるMichiganSolutionを返す．適合度が更新されない場合or適合度が最大値となるMichiganSolutionが複数存在し，
	 * それらの結論部クラスが異なる場合は識別不能とし，nullを返す．
	 * @param michiganSolutionList 識別器
	 * @param attributeVector 入力パターン
	 * @return 勝利となったMichiganSolution 識別不能時はnull
	 */

	private static final double EPS = 1e-16;

	@Override
	public michiganSolution classify(List<michiganSolution> michiganSolutionList, Pattern<?> pattern) {
		if(michiganSolutionList.size() < 1) {
			throw new IllegalArgumentException("argument [michiganSolutionList] has no michiganSolution @SingleWinnerRuleSelection.classify()");}

		boolean canClassify = false; //識別可能フラグ
		double max = -Double.MAX_VALUE; //最大値保存バッファ
		int winner = 0; //勝利ルールインデックス保存バッファ
		for(int q = 0; q < michiganSolutionList.size(); q++) {
			MichiganSolution<?> michiganSolution = michiganSolutionList.get(q);
			if(michiganSolution.getClassLabel().isRejectedClassLabel()) {
				throw new IllegalArgumentException("argument [michiganSolutionList] has michiganSolution with Rejected ClassLabel @SingleWinnerRuleSelection.classify()");}
			double value = michiganSolution.getFitnessValue(pattern.getAttributeVector()); //適合度計算

			//最大値更新ケース
			if(value > max + EPS) {
				max = value;
				winner = q;
				canClassify = true;
			}
			//最大値が同値を取る場合
			else if(Math.abs(value - max) <= EPS) {
				MichiganSolution<?> winnerRule = michiganSolutionList.get(winner);
				// "membership*CF"が同値 かつ 結論部クラスが異なる場合識別不能とする
				if(!michiganSolution.getClassLabel().equalsClassLabel(winnerRule.getClassLabel())) {
					canClassify = false;
				}
			}
		}

		//識別可能である場合勝利ルールを返す
		if(canClassify && max >= 0) {
			return michiganSolutionList.get(winner);
		}
		//識別不可能である場合はnullを返す．
		else {
			return null;
		}
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	@Override
	public Classification<michiganSolution> copy() {
		return new SingleWinnerRuleSelection<michiganSolution>();
	}
}
