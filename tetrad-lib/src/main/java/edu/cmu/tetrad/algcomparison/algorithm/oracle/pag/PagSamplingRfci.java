package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Jan 29, 2023 3:45:09 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PAG-Sampling-RFCI",
        command = "pag-sampling-rfci",
        algoType = AlgType.allow_latent_common_causes
)
@Experimental
public class PagSamplingRfci implements Algorithm, HasKnowledge {

    public static final List<String> RFCI_PARAMETERS = new LinkedList<>();
    public static final List<String> PROBABILISTIC_TEST_PARAMETERS = new LinkedList<>();

    static {
        RFCI_PARAMETERS.add(Params.DEPTH);
        RFCI_PARAMETERS.add(Params.MAX_PATH_LENGTH);
        RFCI_PARAMETERS.add(Params.COMPLETE_RULE_SET_USED);
        RFCI_PARAMETERS.add(Params.TIME_LAG);

        PROBABILISTIC_TEST_PARAMETERS.add(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.CUTOFF_IND_TEST);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
    }

    static final long serialVersionUID = 23L;
    private final IndependenceWrapper test = new ProbabilisticTest();
    private Knowledge knowledge = new Knowledge();

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci pagSamplingRfci = new edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci(DataUtils.getDiscreteDataSet(dataSet));

        // Rfic parameters
        pagSamplingRfci.setKnowledge(this.knowledge);
        pagSamplingRfci.setDepth(parameters.getInt(Params.DEPTH));
        pagSamplingRfci.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        pagSamplingRfci.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        pagSamplingRfci.setNumRandomizedSearchModels(parameters.getInt(Params.NUM_RANDOMIZED_SEARCH_MODELS));

        // ProbabilisticTest parameters
        pagSamplingRfci.setThreshold(parameters.getBoolean(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE));
        pagSamplingRfci.setCutoff(parameters.getDouble(Params.CUTOFF_IND_TEST));
        pagSamplingRfci.setPriorEquivalentSampleSize(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));

        pagSamplingRfci.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return pagSamplingRfci.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return dagToPag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PAG-Sampling-RFCI " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        parameters.addAll(RFCI_PARAMETERS);
        parameters.addAll(PROBABILISTIC_TEST_PARAMETERS);
        parameters.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
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
