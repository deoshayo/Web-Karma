package edu.isi.karma.modeling.semantictypes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.ColumnFeature;
import edu.isi.karma.rep.HNodePath;
import edu.isi.karma.rep.Node;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.semantictypes.SemanticType;
import edu.isi.karma.rep.semantictypes.SemanticTypes;

public class SemanticTypeUtil {

	private static Logger logger = LoggerFactory
			.getLogger(SemanticTypeUtil.class);

	// TODO Make this parameter configurable through web.xml
	private final static int TRAINING_EXAMPLE_MAX_COUNT = 100;

	public static ArrayList<String> getTrainingExamples(Worksheet worksheet,
			HNodePath path) {
		Collection<Node> nodes = new ArrayList<Node>();
		worksheet.getDataTable().collectNodes(path, nodes);

		ArrayList<String> nodeValues = new ArrayList<String>();
		for (Node n : nodes) {
			String nodeValue = n.getValue().asString();
			if (nodeValue != null && !nodeValue.equals(""))
				nodeValues.add(nodeValue);
		}

		// Get rid of duplicate strings by creating a set over the list
		HashSet<String> valueSet = new HashSet<String>(nodeValues);
		nodeValues.clear();
		nodeValues.addAll(valueSet);

		// Shuffling the values so that we get randomly chosen values to train
		Collections.shuffle(nodeValues);

		if (nodeValues.size() > TRAINING_EXAMPLE_MAX_COUNT) {
			ArrayList<String> subset = new ArrayList<String>();
			// SubList method of ArrayList causes ClassCast exception
			for (int i = 0; i < TRAINING_EXAMPLE_MAX_COUNT; i++)
				subset.add(nodeValues.get(i));
			return subset;
		}

		// System.out.println("Examples: " + nodeValues);
		return nodeValues;
	}

	public static boolean populateSemanticTypesUsingCRF(Worksheet worksheet) {
		boolean semanticTypesChangedOrAdded = false;

		SemanticTypes types = worksheet.getSemanticTypes();

		List<HNodePath> paths = worksheet.getHeaders().getAllPaths();

		for (HNodePath path : paths) {
			ArrayList<String> trainingExamples = getTrainingExamples(worksheet,
					path);

			Map<ColumnFeature, Collection<String>> columnFeatures = new HashMap<ColumnFeature, Collection<String>>();

			// Prepare the column name feature
			String columnName = path.getLeaf().getColumnName();
			Collection<String> columnNameList = new ArrayList<String>();
			columnNameList.add(columnName);
			columnFeatures.put(ColumnFeature.ColumnHeaderName, columnNameList);

			// // Prepare the table name feature
			// String tableName = worksheetName;
			// Collection<String> tableNameList = new ArrayList<String>();
			// tableNameList.add(tableName);
			// columnFeatures.put(ColumnFeature.TableName, tableNameList);

			// Stores the probability scores
			ArrayList<Double> scores = new ArrayList<Double>();
			// Stores the predicted labels
			ArrayList<String> labels = new ArrayList<String>();
			boolean predictResult = CRFModelHandler.predictLabelForExamples(
					trainingExamples, 4, labels, scores, null, columnFeatures);
			if (!predictResult) {
				logger.error("Error occured while predicting semantic type.");
			}
			if (labels.size() == 0) {
				continue;
			}

			// Add the scores information to the Full CRF Model of the worksheet
			CRFColumnModel columnModel = new CRFColumnModel(labels, scores);
			worksheet.getCrfModel().addColumnModel(path.getLeaf().getId(),
					columnModel);

			// Create and add the semantic type to the semantic types set of the
			// worksheet
			String topLabel = labels.get(0);
			String domain = "";
			String type = topLabel;
			// Check if it contains domain information
			if (topLabel.contains("|")) {
				domain = topLabel.split("\\|")[0];
				type = topLabel.split("\\|")[1];
			}

			SemanticType semtype = new SemanticType(path.getLeaf().getId(),
					type, domain, SemanticType.Origin.CRFModel, scores.get(0),
					false);

			// Check if the user already provided a semantic type manually
			SemanticType existingType = types.getSemanticTypeByHNodeId(path
					.getLeaf().getId());
			if (existingType == null) {
				if (semtype.getConfidenceLevel() != SemanticType.ConfidenceLevel.Low) {
					worksheet.getSemanticTypes().addType(semtype);
					semanticTypesChangedOrAdded = true;
				}
			} else {
				if (existingType.getOrigin() != SemanticType.Origin.User) {
					worksheet.getSemanticTypes().addType(semtype);

					// Check if the new semantic type is different from the
					// older one
					if (!existingType.getType().equals(semtype.getType())
							|| !existingType.getDomain().equals(
									semtype.getDomain()))
						semanticTypesChangedOrAdded = true;
				}
			}
		}
		return semanticTypesChangedOrAdded;
	}

	public static void prepareCRFModelHandler() throws IOException {
		File file = new File("CRF_Model.txt");
		if (!file.exists()) {
			file.createNewFile();
		}
		boolean result = CRFModelHandler.readModelFromFile("CRF_Model.txt");

		if (!result)
			logger.error("Error occured while reading CRF Model! Aman will allow reading model repeatedly in future.");
	}

	public static String removeNamespace(String uri) {
		if (uri.contains("#"))
			uri = uri.split("#")[1];
		else if (uri.contains("/"))
			uri = uri.substring(uri.lastIndexOf("/") + 1);
		return uri;
	}

}
