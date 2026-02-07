package cilabo.gbml.solution.pittsburghSolution;

import org.uma.jmetal.solution.AbstractSolution;
import org.w3c.dom.Element;

import cilabo.fuzzy.classifier.Classifier;
import cilabo.gbml.solution.michiganSolution.MichiganSolution;
import cilabo.gbml.solution.michiganSolution.MichiganSolution.MichiganSolutionBuilder;
import xml.XML_TagName;

public abstract class AbstractPittsburghSolution <michiganSolution extends MichiganSolution<?>>
		extends AbstractSolution<michiganSolution>
		implements PittsburghSolution<michiganSolution>{

	/** 識別器 */
	protected Classifier<michiganSolution> classifier;
	public MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder;

	/** Constructor */
	protected AbstractPittsburghSolution(int numberOfVariables,
			int numberOfObjectives,
			int numberOfConstraints,
			MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder,
			Classifier<michiganSolution> classifier) {
		super(numberOfVariables, numberOfObjectives, numberOfConstraints);
		this.michiganSolutionBuilder = michiganSolutionBuilder;
		this.classifier = classifier;
	}

	public AbstractPittsburghSolution(int numberOfObjectives,
			int numberOfConstraints,
			MichiganSolutionBuilder<michiganSolution> michiganSolutionBuilder,
			Classifier<michiganSolution> classifier,
			Element pittsburghSolution) {
		super(pittsburghSolution.getElementsByTagName(XML_TagName.michiganSolution.toString()).getLength(),
				numberOfObjectives, numberOfConstraints);
		this.michiganSolutionBuilder = michiganSolutionBuilder;
		this.classifier = classifier;
	}

	@Override
	public MichiganSolutionBuilder<michiganSolution> getMichiganSolutionBuilder() {
		return this.michiganSolutionBuilder;
	}

	@Override
	public void removeVariable(int index) {
		this.variables.remove(index);
	}

	@Override
	public void addVariable(michiganSolution value) {
		this.variables.add(value);
	}

	@Override
	public void clearVariables() {
		this.variables.clear();
	}

	@Override
	public void clearAttributes() {
		this.attributes.clear();
	}

	@Override
	public void learning() {
		for(int i=0; i<this.getNumberOfVariables(); i++) {
			this.variables.get(i).learning();
		}
	}

	/**
	 * CSV形式で1ルールの情報を返す（1ルール1行形式）
	 * @param solutionID Pittsburgh Solutionの番号
	 * @param ruleID ルールの番号（このPittsburgh Solution内での番号）
	 * @param separator 区切り文字
	 * @return CSV形式の文字列
	 */
	public String toCSVString(int solutionID, int ruleID, String separator) {
		StringBuilder str = new StringBuilder();

		// SolutionID, RuleID
		str.append(solutionID).append(separator).append(ruleID);

		// 各属性のファジィ集合ID（Attr0, Attr1, ...）
		michiganSolution rule = this.getVariable(ruleID);
		for (int i = 0; i < rule.getNumberOfVariables(); i++) {
			str.append(separator).append(rule.getVariable(i));
		}

		// ClassLabel
		str.append(separator).append(rule.getRule().getClassLabel().toString());

		// RuleWeight
		str.append(separator).append(rule.getRule().getRuleWeight().getRuleWeightValue());

		// Attributes: NumberOfClassifierPatterns, NumberOfWinner
		// 属性のキーは完全なクラス名（パッケージ名を含む）
		Object numClassifierPatterns = rule.getAttribute("cilabo.gbml.solution.util.attribute.NumberOfClassifierPatterns");
		Object numWinner = rule.getAttribute("cilabo.gbml.solution.util.attribute.NumberOfWinner");

		str.append(separator).append(numClassifierPatterns != null ? numClassifierPatterns.toString() : "0");
		str.append(separator).append(numWinner != null ? numWinner.toString() : "0");

		return str.toString();
	}
}
