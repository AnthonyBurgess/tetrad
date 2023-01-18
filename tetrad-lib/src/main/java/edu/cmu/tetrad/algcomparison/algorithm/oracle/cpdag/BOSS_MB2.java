package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BossMB2;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

///**
// * BOSS-MB.
// *
// * @author jdramsey
// */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BOSS-MB2",
//        command = "boss-mb2",
//        algoType = AlgType.search_for_Markov_blankets
//)
//@Bootstrapping
//@Experimental
public class BOSS_MB2 implements Algorithm, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();

    public BOSS_MB2() {
    }

    public BOSS_MB2(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Score score = this.score.getScore(dataSet, parameters);

            BossMB2 boss = new BossMB2(score);

            boss.setDepth(parameters.getInt(Params.DEPTH));
            boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
            boss.setFindMb(parameters.getBoolean(Params.MB));
            boss.setKnowledge(this.knowledge);

            return boss.search(score.getVariables());
        } else {
            BOSS_MB2 fgesMb = new BOSS_MB2(this.score);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, fgesMb, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
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
        return "BOSS-MB2 using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.MB);

        // Flags
        params.add(Params.DEPTH);
        params.add(Params.CACHE_SCORES);
        params.add(Params.VERBOSE);

        return params;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

}