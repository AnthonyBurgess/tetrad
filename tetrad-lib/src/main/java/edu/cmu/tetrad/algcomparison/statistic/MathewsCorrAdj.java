package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

/**
 * Calculates the Matthew's correlation coefficient for adjacencies. See this page in Wikipedia:
 * <p>
 * https://en.wikipedia.org/wiki/Matthews_correlation_coefficient
 * <p>
 * We calculate the correlation directly from the confusion matrix.
 *
 * @author josephramsey
 */
public class MathewsCorrAdj implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "McAdj";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for adjacencies";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        int adjFn = adjConfusion.getFn();
        int adjTn = adjConfusion.getTn();
        return mcc(adjTp, adjFp, adjTn, adjFn);
    }

    @Override
    public double getNormValue(double value) {
        return 0.5 + 0.5 * value;
    }

    private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
        double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / FastMath.sqrt(b);
    }
}
