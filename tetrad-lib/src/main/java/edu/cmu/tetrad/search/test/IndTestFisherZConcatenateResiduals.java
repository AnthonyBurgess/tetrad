///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates independence from pooled residuals using the Fisher Z method.
 *
 * @author josephramsey
 * @see IndTestFisherZ
 */
public final class IndTestFisherZConcatenateResiduals implements IndependenceTest {


    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;

    private final ArrayList<Regression> regressions;

    private List<DataSet> dataSets;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * The value of the Fisher's Z statistic associated with the last calculated partial correlation.
     */
//    private double fisherZ;

    private double pValue = Double.NaN;
    private boolean verbose;

    /**
     * Constructor.
     *
     * @param dataSets The continuous datasets to analyze.
     * @param alpha    The alpha significance cutoff value.
     */
    public IndTestFisherZConcatenateResiduals(List<DataSet> dataSets, double alpha) {
        System.out.println("# data sets = " + dataSets.size());
        this.dataSets = dataSets;

        this.regressions = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            DataSet _dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()),
                    dataSets.get(0).getVariables());

            this.regressions.add(new RegressionDataset(_dataSet));
        }

        setAlpha(alpha);

        this.variables = dataSets.get(0).getVariables();

        List<DataSet> dataSets2 = new ArrayList<>();

        for (DataSet set : dataSets) {
            DataSet dataSet = new BoxDataSet(new DoubleDataBox(set.getDoubleData().toArray()), this.variables);
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    /**
     * @throws UnsupportedOperationException Not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether x _||_ y | z.
     *
     * @return an independence result
     * @throws org.apache.commons.math3.linear.SingularMatrixException if a matrix singularity is encountered.
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {

        x = getVariable(this.variables, x.getName());
        List<Node> z = GraphUtils.replaceNodes(new ArrayList<>(_z), new ArrayList<>(this.variables));

        // Calculate the residual of x and y conditional on z for each data set and concatenate them.
        double[] residualsX = residuals(x, z);
        double[] residualsY = residuals(y, z);

        List<Double> residualsXFiltered = new ArrayList<>();
        List<Double> residualsYFiltered = new ArrayList<>();

        // This is the way of dealing with missing values; residuals are only correlated
        // for data sets in which both residuals exist.
        for (int i = 0; i < residualsX.length; i++) {
            if (!Double.isNaN(residualsX[i]) && !Double.isNaN(residualsY[i])) {
                residualsXFiltered.add(residualsX[i]);
                residualsYFiltered.add(residualsY[i]);
            }
        }

        residualsX = new double[residualsXFiltered.size()];
        residualsY = new double[residualsYFiltered.size()];

        for (int i = 0; i < residualsXFiltered.size(); i++) {
            residualsX[i] = residualsXFiltered.get(i);
            residualsY[i] = residualsYFiltered.get(i);
        }


        if (residualsX.length != residualsY.length) throw new IllegalArgumentException("Missing values handled.");
        int sampleSize = residualsX.length;

        // return a judgement of whether these concatenated residuals are independent.
        double r = StatUtils.correlation(residualsX, residualsY);

        if (r > 1.) r = 1.;
        if (r < -1.) r = -1.;

        double fisherZ = FastMath.sqrt(sampleSize - z.size() - 3.0) *
                0.5 * (FastMath.log(1.0 + r) - FastMath.log(1.0 - r));

        if (Double.isNaN(fisherZ)) {
            return new IndependenceResult(new IndependenceFact(x, y, _z),
                    true, Double.NaN, Double.NaN);
        }

        double pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, FastMath.abs(fisherZ)));
        this.pValue = pValue;
        boolean independent = pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, this.pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, pValue- getAlpha());

    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
//        this.thresh = Double.NaN;
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @throws UnsupportedOperationException Not implemented.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the concatenated data.
     *
     * @return This data
     */
    public DataSet getData() {
        return DataUtils.concatenate(this.dataSets);
    }

    /**
     * Returns teh covaraince matrix of the concatenated data.
     *
     * @return This covariance matrix.
     */
    @Override
    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<>();

        for (DataSet d : this.dataSets) {
            _dataSets.add(DataUtils.standardizeData(d));
        }

        return new CovarianceMatrix(DataUtils.concatenate(_dataSets));
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher Z, Concatenating Residuals";
    }

    /**
     * Return True if verbose output should be printed.
     *
     * @return True if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private double[] residuals(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node target = this.dataSets.get(0).getVariable(node.getName());

        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : parents) {
            Node variable = this.dataSets.get(0).getVariable(_regressor.getName());
            regressors.add(variable);
        }


        for (int m = 0; m < this.dataSets.size(); m++) {
            RegressionResult result = this.regressions.get(m).regress(target, regressors);
            double[] residualsSingleDataset = result.getResiduals().toArray();

            double mean = StatUtils.mean(residualsSingleDataset);
            for (int i2 = 0; i2 < residualsSingleDataset.length; i2++) {
                residualsSingleDataset[i2] = residualsSingleDataset[i2] - mean;
            }

            for (double d : residualsSingleDataset) {
                _residuals.add(d);
            }
        }

        double[] _f = new double[_residuals.size()];


        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return _f;
    }


    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

}


