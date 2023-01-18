package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;

/**
 * The bidirected false negatives.
 *
 * @author jdramsey
 */
public class BidirectedFP implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BFP";
    }

    @Override
    public String getDescription() {
        return "Number of false positive bidirected edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getFp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}