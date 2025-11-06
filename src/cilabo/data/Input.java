package cilabo.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cilabo.data.pattern.impl.Pattern_Basic;
import cilabo.data.pattern.impl.Pattern_MultiClass;
import cilabo.fuzzy.rule.consequent.classLabel.impl.ClassLabel_Basic;
import cilabo.fuzzy.rule.consequent.classLabel.impl.ClassLabel_Multi;
import cilabo.main.Consts;
import cilabo.main.ExperienceParameter;

/**
 * データセット入力用メソッド群
 * @author Takigawa Hiroki
 */
public class Input {

	/**
	 * <h1>Input File for Classification Dataset</h1>
	 * @param fileName : String
	 * @return 入力済みDataSet
	 */
	public static DataSet<?> inputDataSet(String fileName) {
		switch(ExperienceParameter.classlabel) {
			case Multi:
				return inputDataSet_MultiLabel(fileName);
			case Single:
			default:
				return inputDataSet_Basic(fileName);
		}
	}

	/**
	 * <h1>Input File for Single-Label Classification Dataset</h1>
	 * @param fileName : String
	 * @return 入力済みDataSet
	 */
	public static DataSet<Pattern_Basic> inputDataSet_Basic(String fileName) {
		List<double[]> lines = inputDataAsList(fileName);

		// The first row is parameters of dataset
		DataSet<Pattern_Basic> data = new DataSet<Pattern_Basic>(
				(int)lines.get(0)[0],
				(int)lines.get(0)[1],
				(int)lines.get(0)[2]);
		lines.remove(0);

		// Later second row are patterns
		for(int n = 0; n < data.getDataSize(); n++) {
			double[] line = lines.get(n);

			int id = n;
			double[] vector = new double[data.getNdim()];
			Integer C;
			for(int i = 0; i < vector.length; i++) {
				vector[i] = line[i];
			}
			C = (int)line[data.getNdim()];

			AttributeVector inputVector = new AttributeVector(vector);
			ClassLabel_Basic classLabel = new ClassLabel_Basic(C);

			Pattern_Basic pattern = new Pattern_Basic(
					id,
					inputVector,
					classLabel);
			data.addPattern(pattern);
		}
		return data;
	}

	/**
	 * <h1>Input File for Multi-Label Classification Dataset</h1>
	 * @param fileName : String
	 * @return 入力済みDataSet
	 */
	public static DataSet<Pattern_MultiClass> inputDataSet_MultiLabel(String fileName) {
		List<double[]> lines = inputDataAsList(fileName);

		// The first row is parameters of dataset
		DataSet<Pattern_MultiClass> data = new DataSet<Pattern_MultiClass>(
				(int)lines.get(0)[0],
				(int)lines.get(0)[1],
				(int)lines.get(0)[2]);
		lines.remove(0);

		// Later second row are patterns
		for(int n = 0; n < data.getDataSize(); n++) {
			double[] line = lines.get(n);

			int id = n;
			double[] vector = new double[data.getNdim()];
			Integer[] cVec = new Integer[data.getCnum()];
			for(int i = 0; i < vector.length; i++) {
				vector[i] = line[i];
			}
			for(int i = 0; i < data.getCnum(); i++) {
				cVec[i] = (int)line[i + data.getNdim()];
			}

			AttributeVector inputVector = new AttributeVector(vector);
			ClassLabel_Multi classLabel = new ClassLabel_Multi(cVec);

			Pattern_MultiClass pattern = new Pattern_MultiClass(
					id,
					inputVector,
					classLabel);
			data.addPattern(pattern);
		}
		return data;
	}

	/**
	 * ファイル名を指定して複数クラスラベルのデータセットをロード
	 * @param trainFile 学習用データセットのパス
	 * @param testFile 評価用データセットのパス
	 */
	public static void loadTrainTestFiles_Basic(String trainFile, String testFile) {

		/* Load Dataset ======================== */
		if(Objects.isNull(DataSetManager.getInstance().getTrains())) {
			throw new IllegalArgumentException("argument [trainFile] is null @" + "TrainTestDatasetManager.loadTrainTestFiles()");}
		if(Objects.isNull(DataSetManager.getInstance().getTests())) {
			throw new IllegalArgumentException("argument [testFile] is null @" + "TrainTestDatasetManager.loadTrainTestFiles()");}

		DataSet<Pattern_Basic> train = Input.inputDataSet_Basic(trainFile);
		DataSetManager.getInstance().addTrains(train);
		Consts.DATA_SIZE = train.getDataSize();
		Consts.ATTRIBUTE_NUMBER = train.getNdim();
		Consts.CLASS_LABEL_NUMBER = train.getCnum();

		DataSet<Pattern_Basic> test = Input.inputDataSet_Basic(testFile);
		DataSetManager.getInstance().addTests(test);

		if(Objects.isNull(DataSetManager.getInstance().getTrains())) {
			throw new IllegalArgumentException("failed to initialise trains@TrainTestDatasetManager.loadTrainTestFiles()");}
		else if(Objects.isNull(DataSetManager.getInstance().getTests())) {
			throw new IllegalArgumentException("failed to initialise tests@TrainTestDatasetManager.loadTrainTestFiles()");}
		return;
	}

	/**
	 * ファイル名を指定して単一クラスラベルのデータセットをロード
	 * @param trainFile 学習用データセットのパス
	 * @param testFile 評価用データセットのパス
	 */
	public static void loadTrainTestFiles_MultiClass(String trainFile, String testFile) {

		/* Load Dataset ======================== */
		if(Objects.isNull(DataSetManager.getInstance().getTrains())) {
			throw new IllegalArgumentException("argument [trainFile] is null @TrainTestDatasetManager.loadTrainTestFiles()");}
		if(Objects.isNull(DataSetManager.getInstance().getTests())) {
			throw new IllegalArgumentException("argument [testFile] is null @TrainTestDatasetManager.loadTrainTestFiles()");}

		DataSet<Pattern_MultiClass> train = Input.inputDataSet_MultiLabel(trainFile);
		DataSetManager.getInstance().addTrains(train);
		Consts.DATA_SIZE = train.getDataSize();
		Consts.ATTRIBUTE_NUMBER = train.getNdim();
		Consts.CLASS_LABEL_NUMBER = train.getCnum();

		DataSet<Pattern_MultiClass> test = Input.inputDataSet_MultiLabel(testFile);
		DataSetManager.getInstance().addTests(test);

		if(Objects.isNull(DataSetManager.getInstance().getTrains())) {
			throw new IllegalArgumentException("failed to initialise trains@TrainTestDatasetManager.loadTrainTestFiles()");}
		else if(Objects.isNull(DataSetManager.getInstance().getTests())) {
			throw new IllegalArgumentException("failed to initialise tests@TrainTestDatasetManager.loadTrainTestFiles()");}
		return;
	}

//	public static DataSet inputSubdata(DataSet origin, DataSet divided, String fileName) {
//		List<double[]> lines = inputDataAsList(fileName);
//
//		// The first row is parameters of dataset
//		divided.setDataSize( (int)lines.get(0)[0] );
//		divided.setNdim( (int)lines.get(0)[1] );
//		divided.setCnum( (int)lines.get(0)[2] );
//		lines.remove(0);
//
//		// Later second row are patterns
//		for(int n = 0; n < divided.getDataSize(); n++) {
//			int id = (int)lines.get(n)[0];
//			Pattern pattern = origin.getPatternWithID(id);
//			divided.addPattern(pattern);
//		}
//		return divided;
//	}

//	/**
//	 * 引数に与えられた試行回数に応じたファイル名を作成するメソッド
//	 * @param dataName
//	 * @param cv_i
//	 * @param rep_i
//	 * @param isTra
//	 * @return String : a"rep_i"_"cv_i"_"dataName"-10"isTra"に応じたファイル名
//	 */
//	public static String makeFileNameOne(String dataName, int cv_i, int rep_i, boolean isTra) {
//		String sep = File.separator;
//		String fileName = "";
//		if(isTra) {
//			fileName = System.getProperty("user.dir") + sep + "dataset" + sep + dataName + sep + "a" + rep_i + "_" + cv_i + "_" + dataName + "-10tra.dat";
//		} else {
//			fileName = System.getProperty("user.dir") + sep + "dataset" + sep + dataName + sep + "a" + rep_i + "_" + cv_i + "_" + dataName + "-10tst.dat";
//		}
//		return fileName;
//	}

//	private static List<String> inputDataAsListString(String fileName) {
//		List<String> lines = new ArrayList<>();
//		try ( Stream<String> line = Files.lines(Paths.get(fileName)) ) {
//		    lines = line.collect( Collectors.toList() );
//		} catch (IOException e) {
//		    e.printStackTrace();
//		}
//		return lines;
//	}

	/**
	 *
	 * @param fileName
	 * @return : List{@literal <double[]>}
	 */
	public static List<double[]> inputDataAsList(String fileName) {
		List<double[]> lines = new ArrayList<double[]>();
		try ( Stream<String> line = Files.lines(Paths.get(fileName)) ) {
		    lines =
		    	line.map(s ->{
		    	String[] numbers = s.split(",");
		    	double[] nums = new double[numbers.length];

		    	//値が無い場合の例外処理
		    	for (int i = 0; i < nums.length; i++) {
		    			nums[i] = Double.parseDouble(numbers[i]);
				}
		    	return nums;
		    })
		    .collect( Collectors.toList() );
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return lines;
	}
}
