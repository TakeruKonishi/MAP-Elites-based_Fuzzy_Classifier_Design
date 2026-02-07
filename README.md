# MAP-Elites-based Fuzzy Classifier Design
**本リポジトリについて**

AIが金融や医療診断といった高リスクの意思決定に利用される際，AIの信頼性を保証するために透明性が求められます．特に，本質的に解釈可能なモデルは，内部機構が人間に理解可能なため，説明責任が求められる分野において有用です．ファジィシステムは，現実世界の不確実性を考慮した柔軟な意思決定を可能にする代表的な本質的に解釈可能なモデルです．MAP-Elitesに基づくファジィ識別器設計手法（MAP-Elites-based Fuzzy Classifier design: MEFC）は，MAP-Elitesを用いることで，選択した行動記述子に関して多様かつ高精度なファジィ識別器集合を生成します．本リポジトリでは，説明可能性指標及び解釈可能性指標によって定義された2次元行動記述子空間において，精度の高いファジィ識別器を探索するMEFCを実行することができます．

**依存関係について**  

jMetal等のライブラリを使用しているため，ソースコードだけでは，依存関係でエラーが起こるかもしれません．Mavenというビルドツールを使っているので，依存関係のエラーを解決する手間が省けていると思います．


**簡単な使い方** 

本リポジトリはMavenプロジェクトになっているので，EclipseなどのIDEを使って開いてください．pom.xmlに依存関係を定義しているので，Eclipseであれば，`pom.xmlを右クリック → 実行 → 3 Maven install`を実行することで，依存関係のエラーが解決できます．


**実行可能JARファイルの生成**

まず，pom.xmlのJARファイル名，main関数を指定してください．次に，`pom.xmlを右クリック → 実行 → 6 Maven build`を実行し，ゴールにpackageを指定して実行してください．その後，targetディレクトリ内に実行可能JARファイルとその他必要な依存関係ライブラリが生成されるので，適宜実験を行ってください．


**その他**  

適宜データセットを追加，constsを変更して使用してください．

# MAP-Elites-based Fuzzy Classifier Design
**About this repository**

When AI is used for high-risk decisions such as finance and medical diagnosis, transparency is required to ensure its reliability. In particular, inherently interpretable models are useful where accountability is demanded, as their internal mechanisms are understandable to humans. Fuzzy systems are representative inherently interpretable models that can make flexible decisions considering real-world uncertainties. MAP-Elites-based Fuzzy Classifier design (MEFC) generates a set of fuzzy classifiers that are both diverse regarding selected behavior descriptors and highly accurate by using MAP-Elites. Using this repository, you can perform MEFC that searches for accurate fuzzy classifiers within a two-dimensional behavior descriptor space defined by explainability and interpretability measures.

**About dependencies**

Since we are using libraries such as jMetal, dependency errors may occur in the source code alone. We use a build tool called Maven, which saves us the trouble of resolving dependency errors.


**Simple usage**  

Open this repository as a Maven project using an IDE such as Eclipse. Since the dependencies are defined in pom.xml, you can resolve the dependency errors by `right-clicking pom.xml → Run → 3 Maven install` if you are using Eclipse.


**Generation of executable JAR file**  

First, specify the JAR file name and main function in pom.xml'. Next, `right-click pom.xml → Run → 6 Maven build` and specify package as the goal. This will generate an executable JAR file and other necessary dependency libraries in the target directory. Please conduct your experiments.


**Other**  

Please add datasets and change consts as necessary.