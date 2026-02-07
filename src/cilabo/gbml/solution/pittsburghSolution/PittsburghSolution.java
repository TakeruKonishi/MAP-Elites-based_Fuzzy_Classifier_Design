package cilabo.gbml.solution.pittsburghSolution;

import java.util.List;

import org.uma.jmetal.solution.Solution;
import org.w3c.dom.Element;

import cilabo.data.pattern.Pattern;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;

/**
 * @author Takigawa Hiroki
 *
 * @param <michiganSolution>
 */
public interface PittsburghSolution <michiganSolution extends MichiganSolution<?>>
	extends Solution<michiganSolution> {

	@Override
	public void setVariable(int index, michiganSolution value);

	@Override
	public michiganSolution getVariable(int index);

	@Override
	public List<michiganSolution> getVariables();

	/** 指定したインデックスのMichiganSolutionを削除する
	 * @param index 削除したいMichiganSolutionのインデックス
	 */
	public void removeVariable(int index);

	/** MichiganSolutionを追加する
	 * @param value 追加したいMichiganSolution
	 */
	public void addVariable(michiganSolution value);

	/** MichiganSolutionを初期化する */
	public void clearVariables();

	/** Attributeを初期化する */
	public void clearAttributes();

	/**
	 * このインスタンスが持つミシガン型識別器ジェネレーターを返します。<br>
	 * Returns michiganSolution builder that this instance has.
	 * @return 返されるミシガン型識別器ジェネレーター．michiganSolution builder to return
	 */
	public MichiganSolutionBuilder<michiganSolution> getMichiganSolutionBuilder();

	/** このインスタンスが持つ全てのMichiganSolutionを対象に前件部を基に後件部の学習を行う */
	public void learning();

	/** 識別を行い勝者となったMichiganSolutionを返す
	 * @param pattern 識別対象となるパターンクラス
	 * @return 勝者となったMichiganSolution 識別不能時はnullを返す
	 */
	public michiganSolution classify(Pattern<?> pattern);

	public Element toElement();

	/**
	 * CSV形式で1ルールの情報を返す（1ルール1行形式）
	 * @param solutionID Pittsburgh Solutionの番号
	 * @param ruleID ルールの番号（このPittsburgh Solution内での番号）
	 * @param separator 区切り文字
	 * @return CSV形式の文字列
	 */
	public String toCSVString(int solutionID, int ruleID, String separator);

	@Override
	public PittsburghSolution<michiganSolution> copy();
}
