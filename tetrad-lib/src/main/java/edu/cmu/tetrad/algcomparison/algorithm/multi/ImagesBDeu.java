package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BdeuScoreImages;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for discrete variables.
 *
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "IMaGES Discrete",
//        command = "imgs_disc",
//        algoType = AlgType.forbid_latent_common_causes,
//        dataType = DataType.Discrete
//)
@Bootstrapping
@Experimental
public class ImagesBDeu implements MultiDataSetAlgorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    public ImagesBDeu() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            BdeuScoreImages score = new BdeuScoreImages(dataSets);
            score.setSamplePrior(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
            score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
            search.setFaithfulnessAssumed(true);
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            ImagesBDeu imagesBDeu = new ImagesBDeu();

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                datasets.add((DataSet) dataModel);
            }
            GeneralResamplingTest search = new GeneralResamplingTest(
                    datasets,
                    imagesBDeu,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(null);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {

    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getDiscreteDataSet(dataSet)), parameters);
        } else {
            ImagesBDeu imagesBDeu = new ImagesBDeu();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    imagesBDeu,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(null);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "IMaGES for discrete variables (using the BDeu score)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.addAll((new Fges()).getParameters());
        parameters.addAll((new BdeuScore()).getParameters());
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
