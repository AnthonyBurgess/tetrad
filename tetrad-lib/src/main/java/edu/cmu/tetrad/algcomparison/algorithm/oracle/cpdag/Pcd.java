package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
@Bootstrapping
public class Pcd implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    public Pcd() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            IndTestScore test;

            if (dataSet instanceof ICovarianceMatrix) {
                SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataSet);
                score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
                score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
                test = new IndTestScore(score);
            } else if (dataSet instanceof DataSet) {
                SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
                score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
                score.setDeterminismThreshold(parameters.getDouble(Params.DETERMINISM_THRESHOLD));
                test = new IndTestScore(score);
            } else {
                throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
            }

            edu.cmu.tetrad.search.Pcd search = new edu.cmu.tetrad.search.Pcd(test);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else {
            Pcd algorithm = new Pcd();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.cpdagForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\") Deternimistic";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.DEPTH);
        parameters.add(Params.DETERMINISM_THRESHOLD);

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
