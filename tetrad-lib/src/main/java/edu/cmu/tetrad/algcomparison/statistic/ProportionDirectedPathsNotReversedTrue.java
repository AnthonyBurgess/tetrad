package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class ProportionDirectedPathsNotReversedTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NRTE";
    }

    @Override
    public String getDescription() {
        return "Proportion of exists semidirected(Y, X) in true for which no semidirected(X, Y) in estimated";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = trueGraph.getNodes();
        int tp = 0;
        int fn = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                    if (!estGraph.existsSemiDirectedPathFromTo(y, Collections.singleton(x))) {
                        tp++;
                    } else {
                        fn++;
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
