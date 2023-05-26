package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.search.work_in_progress.IndTestIod;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Runs IOD on multiple datasets. The reference is here:</p>
 *
 * <p>Tillman, R., & Spirtes, P. (2011, June). Learning equivalence classes of acyclic models with latent and selection
 * variables from multiple datasets with overlapping variables. In Proceedings of the Fourteenth International
 * Conference on Artificial Intelligence and Statistics (pp. 3-15). JMLR Workshop and Conference Proceedings.</p>
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.work_in_progress.IndTestIod
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IOD",
        command = "iod",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class Iod implements MultiDataSetAlgorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    private IndependenceWrapper test;

    public Iod(IndependenceWrapper test) {
        this.test = test;
    }

    public Iod() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataModel> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataModel dataSet : dataSets) {
                    DataSet timeSeries = TsUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets = _dataSets;
            }

            List<IndependenceTest> tests = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                IndependenceTest s = test.getTest(dataModel, parameters);
                tests.add(s);
            }

            IndTestIod test = new IndTestIod(tests);

            Fci fci = new Fci(test);

            fci.setKnowledge(knowledge);

            fci.setVerbose(parameters.getBoolean(Params.VERBOSE));

            fci.setDepth(parameters.getInt(Params.DEPTH));

            fci.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));

            fci.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));

            return fci.search();

        } else {
            Iod imagesSemBic = new Iod();

            List<DataSet> dataSets2 = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                dataSets2.add((DataSet) dataModel);
            }

            List<DataSet> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataSet dataSet : dataSets2) {
                    DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets2 = _dataSets;
            }

            GeneralResamplingTest search = new GeneralResamplingTest(
                    dataSets2,
                    imagesSemBic,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(this.knowledge);
            search.setIndTestWrapper(test);
            return search.search();
        }
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        // Not used.
    }

    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
        } else {
            Iod images = new Iod();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    images,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT),
                    parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
//            search.setScoreWrapper(score);
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "IOD";
    }

    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.addAll(new SemBicScore().getParameters());

        parameters.addAll((new Fges()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.IMAGES_META_ALG);

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

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
