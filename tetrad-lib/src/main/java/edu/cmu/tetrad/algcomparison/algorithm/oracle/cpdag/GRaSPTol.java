package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * GRaSP (Greedy Relaxations of Sparsest Permutation)
 *
 * @author bryanandrews
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GRaSP-Tol",
        command = "grasptol",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class GRaSPTol implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();

    public GRaSPTol() {
        // Used in reflection; do not delete.
    }

    public GRaSPTol(ScoreWrapper score, IndependenceWrapper test) {
        this.score = score;
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
                DataSet timeSeries = TimeSeriesUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                dataModel = timeSeries;
                knowledge = timeSeries.getKnowledge();
            }

            Score score = this.score.getScore(dataModel, parameters);
            IndependenceTest test = this.test.getTest(dataModel, parameters);

            test.setVerbose(parameters.getBoolean(Params.VERBOSE));
            GraspTol grasp = new GraspTol(test, score);

            grasp.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            grasp.setUncoveredDepth(parameters.getInt(Params.GRASP_SINGULAR_DEPTH));
            grasp.setNonSingularDepth(parameters.getInt(Params.GRASP_NONSINGULAR_DEPTH));
            grasp.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
            grasp.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            grasp.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_RASKUTTI_UHLER));
            grasp.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            grasp.setToleranceDepth(parameters.getInt(Params.GRASP_TOLERANCE_DEPTH));
            grasp.setAllowRandomnessInsideAlgorithm(parameters.getBoolean(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM));
            grasp.setVerbose(parameters.getBoolean(Params.VERBOSE));
            grasp.setCacheScores(parameters.getBoolean(Params.CACHE_SCORES));

            grasp.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            grasp.setKnowledge(this.knowledge);
            grasp.bestOrder(score.getVariables());
            return grasp.getGraph(parameters.getBoolean(Params.OUTPUT_CPDAG));
        } else {
            GRaSPTol algorithm = new GRaSPTol(this.score, this.test);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);


            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "GRaSPTol (Greedy Relaxed Sparsest Permutation Tolerance) using " + this.test.getDescription()
                + " or " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_SINGULAR_DEPTH);
        params.add(Params.GRASP_NONSINGULAR_DEPTH);
        params.add(Params.GRASP_TOLERANCE_DEPTH);
        params.add(Params.GRASP_ORDERED_ALG);
//        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM);
        params.add(Params.CACHE_SCORES);
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
