///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.sqrt;

/**
 * A special SEM model in which variances of variables are always 1 and means of variables
 * are always 0. In order to ensure that means of variables are always zero, means or error
 * terms are set to zero. (They are alway Gaussian for this model.) Connection functions are
 * always linear. In order to ensure that variances of variables are always 1, only coefficients
 * are allowed to change, and the error terms take up the slack. Becuase of this constraint,
 * given settings of other freeParameters, the range of a given parameter is always bounded
 * above and below. The user may query this range and set set the value of the coefficient
 * to anything within this range. The SEM is initialized from a linear, gaussian SEM by
 * calculating (or estimating) what the coefficients would be if a data set were simulated
 * from that SEM, standardized, and reestimated with the same SEM PM. The coefficients of
 * such an estimated SEM PM are used to initialize the standardized SEM, repeating if necessary
 * (due to possible noise issues) to get coefficients for which all errors variances can
 * be calculated. (Variances need to be >= 0 for Normal distributions.) This produces a
 * set of coefficients that are viable candidates for the standardized SEM. From there,
 * the user cannot make any change that does not also allow for a standardized SEM to be
 * defined, with error variances taking up the slack. Thus, the standardized SEM can
 * never go "out of bounds."
 * </p>
 * Currently we are not allowing bidirected edges in the SEM graph.
 */
public class StandardizedSemIm implements Simulator, TetradSerializable {
    private Matrix edgeCoef;
    private Matrix errorCovar;
    private Map<Node, Double> errorVariances;
    private final int sampleSize;

    public int getSampleSize() {
        return sampleSize;
    }

    public enum Initialization {
        CALCULATE_FROM_SEM, INITIALIZE_FROM_DATA
    }

    static final long serialVersionUID = 23L;

    /**
     * The SEM model.
     */
    private final SemPm semPm;

    /**
     * The graph of the model. Stored internally because it must be ensured that the error terms are showing.
     */
    private final SemGraph semGraph;

    /**
     * A map from edges in the graph to their coefficients. These are the only freeParameters in the model. This
     * includes coefficients for directed as well as bidirected edges.
     */
    private final Map<Edge, Double> edgeParameters;

    /**
     * A map from error nodes in the graph to their error variances. These are not freeParameters in the model; their
     * values are always calculated as residual variances, under the constraint that the variances of each
     * variable must be 1.
     */
//    private Map<Node, Double> errorVariances;

    private Matrix implCovar;
    private Matrix implCovarMeas;
    private Edge editingEdge;
    private ParameterRange range;


    //========================================CONSTRUCTORS========================================================//

    /**
     * Constructs a new standardized SEM IM, initializing from the freeParameters in the given SEM IM.
     *
     * @param im The SEM IM that the freeParameters will be initialized from.
     */
    public StandardizedSemIm(SemIm im, Parameters parameters) {
        this(im, Initialization.CALCULATE_FROM_SEM, parameters);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static StandardizedSemIm serializableInstance() {
        return new StandardizedSemIm(SemIm.serializableInstance(), new Parameters());
    }

    //===========================================PUBLIC METHODS==================================================//

    /**
     * Constructs a new standardized SEM IM from the freeParameters in the given SEM IM.
     *
     * @param im             Stop asking me for these things! The given SEM IM!!!
     * @param initialization CALCULATE_FROM_SEM if the initial values will be calculated from the given SEM IM;
     */
    public StandardizedSemIm(SemIm im, Initialization initialization, Parameters parameters) {
        semPm = new SemPm(im.getSemPm());
        semGraph = new SemGraph(semPm.getGraph());
        semGraph.setShowErrorTerms(true);
        sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        if (semGraph.existsDirectedCycle()) {
            throw new IllegalArgumentException("The cyclic case is not handled.");
        }

        if (initialization == Initialization.CALCULATE_FROM_SEM) {
//          This code calculates the new coefficients directly from the old ones.
            edgeParameters = new HashMap<>();

            List<Node> nodes = im.getVariableNodes();
            Matrix impliedCovar = im.getImplCovar(true);

            for (Parameter parameter : im.getSemPm().getParameters()) {
                if (parameter.getType() == ParamType.COEF) {
                    Node a = parameter.getNodeA();
                    Node b = parameter.getNodeB();
                    int aindex = nodes.indexOf(a);
                    int bindex = nodes.indexOf(b);
                    double vara = impliedCovar.get(aindex, aindex);
                    double stda = sqrt(vara);
                    double varb = impliedCovar.get(bindex, bindex);
                    double stdb = sqrt(varb);
                    double oldCoef = im.getEdgeCoef(a, b);
                    double newCoef = (stda / stdb) * oldCoef;
                    edgeParameters.put(Edges.directedEdge(a, b), newCoef);
                } else if (parameter.getType() == ParamType.COVAR) {
                    Node a = parameter.getNodeA();
                    Node b = parameter.getNodeB();
                    Node exoa = semGraph.getExogenous(a);
                    Node exob = semGraph.getExogenous(b);
                    double covar = im.getErrCovar(a, b) / sqrt(im.getErrVar(a) * im.getErrVar(b));
                    edgeParameters.put(Edges.bidirectedEdge(exoa, exob), covar);
                }
            }
        } else {

            // This code estimates the new coefficients from simulated data from the old model.
            DataSet dataSet = im.simulateData(sampleSize, false);
            Matrix _dataSet = dataSet.getDoubleData();
            _dataSet = DataUtils.standardizeData(_dataSet);
            DataSet dataSetStandardized = new BoxDataSet(new VerticalDoubleDataBox(_dataSet.toArray()), dataSet.getVariables());

            SemEstimator estimator = new SemEstimator(dataSetStandardized, im.getSemPm());
            SemIm imStandardized = estimator.estimate();

            edgeParameters = new HashMap<>();

            for (Parameter parameter : imStandardized.getSemPm().getParameters()) {
                if (parameter.getType() == ParamType.COEF) {
                    Node a = parameter.getNodeA();
                    Node b = parameter.getNodeB();
                    double coef = imStandardized.getEdgeCoef(a, b);
                    edgeParameters.put(Edges.directedEdge(a, b), coef);
                } else if (parameter.getType() == ParamType.COVAR) {
                    Node a = parameter.getNodeA();
                    Node b = parameter.getNodeB();
                    Node exoa = semGraph.getExogenous(a);
                    Node exob = semGraph.getExogenous(b);
                    double covar = -im.getErrCovar(a, b) / sqrt(im.getErrVar(a) * im.getErrVar(b));
                    edgeParameters.put(Edges.bidirectedEdge(exoa, exob), covar);
                }
            }
        }
    }

    public boolean containsParameter(Edge edge) {
        if (Edges.isBidirectedEdge(edge)) {
            edge = Edges.bidirectedEdge(semGraph.getExogenous(edge.getNode1()),
                    semGraph.getExogenous(edge.getNode2()));
        }

        return edgeParameters.containsKey(edge);
    }

    /**
     * Sets the coefficient for the a->b edge to the given coefficient, if within range. Otherwise
     * does nothing.
     *
     * @param a    a -> b
     * @param b    a -> b
     * @param coef The coefficient of a -> b.
     * @return true if the coefficent was set (i.e. was within range), false if not.
     */
    public boolean setEdgeCoefficient(Node a, Node b, double coef) {
        Edge edge = Edges.directedEdge(a, b);

        if (edgeParameters.get(edge) == null) {
            throw new NullPointerException("Not a coefficient parameter in this model: " + edge);
        }

        if (!edge.equals(editingEdge)) {
            range = this.getParameterRange(edge);
            editingEdge = edge;
        }

        if (coef > range.getLow() && coef < range.getHigh()) {
            edgeParameters.put(edge, coef);
            return true;
        }

        return false;
    }

    /**
     * Sets the covariance for the a<->b edge to the given covariance, if within range. Otherwise
     * does nothing.
     *
     * @param a     a <-> b
     * @param b     a <-> b
     * @param covar The covariance of a <-> b.
     * @return true if the coefficent was set (i.e. was within range), false if not.
     */
    public boolean setErrorCovariance(Node a, Node b, double covar) {
        Edge edge = Edges.bidirectedEdge(semGraph.getExogenous(a), semGraph.getExogenous(b));

        if (edgeParameters.get(edge) == null) {
            throw new IllegalArgumentException("Not a covariance parameter in this model: " + edge);
        }

        if (!edge.equals(editingEdge)) {
            range = this.getParameterRange(edge);
            editingEdge = edge;
        }

        if (covar > range.getLow() && covar < range.getHigh()) {
            edgeParameters.put(edge, covar);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param a a->b
     * @param b a->b
     * @return The coefficient for a->b.
     */
    public double getEdgeCoef(Node a, Node b) {
        Edge edge = Edges.directedEdge(a, b);
        Double d = edgeParameters.get(edge);

        if (d == null) {
            return Double.NaN;
//            throw new IllegalArgumentException("Not a directed edge in this model: " + edge);
        }

        return d;
    }

    /**
     * @param a a->b
     * @param b a->b
     * @return The coefficient for a->b.
     */
    public double getErrorCovariance(Node a, Node b) {
        Edge edge = Edges.bidirectedEdge(semGraph.getExogenous(a), semGraph.getExogenous(b));
        Double d = edgeParameters.get(edge);

        if (d == null) {
            throw new IllegalArgumentException("Not a covariance parameter in this model: " + edge);
        }

        return d;
    }

    public double getParameterValue(Edge edge) {
        if (Edges.isDirectedEdge(edge)) {
            return this.getEdgeCoef(edge.getNode1(), edge.getNode2());
        } else if (Edges.isBidirectedEdge(edge)) {
            return this.getErrorCovariance(edge.getNode1(), edge.getNode2());
        } else {
            throw new IllegalArgumentException("Only directed and bidirected edges are supported: " + edge);
        }
    }

    public void setParameterValue(Edge edge, double value) {
        if (Edges.isDirectedEdge(edge)) {
            this.setEdgeCoefficient(edge.getNode1(), edge.getNode2(), value);
        } else if (Edges.isBidirectedEdge(edge)) {
            this.setErrorCovariance(edge.getNode1(), edge.getNode2(), value);
        } else {
            throw new IllegalArgumentException("Only directed and bidirected edges are supported: " + edge);
        }
    }

    public ParameterRange getCoefficientRange(Node a, Node b) {
        return this.getParameterRange(Edges.directedEdge(a, b));
    }

    public ParameterRange getCovarianceRange(Node a, Node b) {
        return this.getParameterRange(Edges.bidirectedEdge(semGraph.getExogenous(a), semGraph.getExogenous(b)));
    }

    /**
     * @param edge a->b or a<->b.
     * @return the range of the covariance parameter for a->b or a<->b.
     */
    public ParameterRange getParameterRange(Edge edge) {
        if (Edges.isBidirectedEdge(edge)) {
            edge = Edges.bidirectedEdge(semGraph.getExogenous(edge.getNode1()),
                    semGraph.getExogenous(edge.getNode2()));
        }


        if (!(edgeParameters.containsKey(edge))) {
            throw new IllegalArgumentException("Not an edge in this model: " + edge);
        }

        double initial = edgeParameters.get(edge);

        if (initial == Double.NEGATIVE_INFINITY) {
            initial = Double.MIN_VALUE;
        } else if (initial == Double.POSITIVE_INFINITY) {
            initial = Double.MAX_VALUE;
        }

        double value = initial;

        // look upward for a point that fails.
        double high = value + 1;

        while (this.paramInBounds(edge, high)) {
            high = value + 2 * (high - value);

            if (high == Double.POSITIVE_INFINITY) {
                break;
            }
        }

        // find the boundary using binary search.
        double rangeHigh;

        if (high == Double.POSITIVE_INFINITY) {
            rangeHigh = high;
        } else {
            double low = value;

            while (high - low > 1e-10) {
                double midpoint = (high + low) / 2.0;

                if (this.paramInBounds(edge, midpoint)) {
                    low = midpoint;
                } else {
                    high = midpoint;
                }
            }

            rangeHigh = (high + low) / 2.0;
        }

        // look downard for a point that fails.
        double low = value - 1;

        while (this.paramInBounds(edge, low)) {
            low = value - 2 * (value - low);

            if (low == Double.NEGATIVE_INFINITY) {
                break;
            }
        }

        double rangeLow;

        if (low == Double.NEGATIVE_INFINITY) {
            rangeLow = low;
        } else {

            // find the boundary using binary search.
            high = value;

            while (high - low > 1e-10) {
                double midpoint = (high + low) / 2.0;

                if (this.paramInBounds(edge, midpoint)) {
                    high = midpoint;
                } else {
                    low = midpoint;
                }
            }

            rangeLow = (high + low) / 2.0;
        }

        if (Edges.isDirectedEdge(edge)) {
            edgeParameters.put(edge, initial);
        } else if (Edges.isBidirectedEdge(edge)) {
            edgeParameters.put(edge, initial);
        }

        return new ParameterRange(edge, value, rangeLow, rangeHigh);
    }

    /**
     * @param error The error term. A node with NodeType.ERROR.
     * @return the error variance for the given error term. THIS IS NOT A PARAMETER OF THE
     * MODEL! Its value is simply calculated from the given coefficients of the model.
     * Returns Double.NaN if the error variance cannot be computed.
     */
    public double getErrorVariance(Node error) {
        return this.calculateErrorVarianceFromParams(error);
    }

    /**
     * @return a map from error to error variances, or to Double.NaN where these cannot be computed.
     */
    private Map<Node, Double> errorVariances() {
        if (errorVariances != null) {
            return errorVariances;
        }

        Map<Node, Double> errorVarances = new HashMap<>();

        for (Node error : this.getErrorNodes()) {
            errorVarances.put(error, this.getErrorVariance(error));
        }

        errorVariances = errorVarances;
        return errorVarances;
    }

    /**
     * @return a string representation of the coefficients and variances of the model.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        buf.append("\nStandardized SEM:");
        buf.append("\n\nEdge coefficients (parameters):\n");

        for (Edge edge : edgeParameters.keySet()) {
            if (!Edges.isDirectedEdge(edge)) {
                continue;
            }

            buf.append("\n").append(edge).append(" ").append(nf.format(edgeParameters.get(edge)));
        }

        buf.append("\n\nError covariances (parameters):\n");

        for (Edge edge : edgeParameters.keySet()) {
            if (!Edges.isBidirectedEdge(edge)) {
                continue;
            }

            buf.append("\n").append(edge).append(" ").append(nf.format(edgeParameters.get(edge)));
        }

        buf.append("\n\nError variances (calculated):\n");

        for (Node error : this.getErrorNodes()) {
            double variance = this.getErrorVariance(error);
            buf.append("\n").append(error).append(" ").append(Double.isNaN(variance) ? "Undefined" : nf.format(variance));
        }

        buf.append("\n");

        return buf.toString();
    }

    /**
     * @return the list of variable nodes of the model, in order.
     */
    public List<Node> getVariableNodes() {
        return semPm.getVariableNodes();
    }

    /**
     * @return The edge coefficient matrix of the model, a la SemIm. Note that this will normally need to be
     * transposed, since [a][b] is the edge coefficient for a-->b, not b-->a. Sorry. History. THESE ARE
     * PARAMETERS OF THE MODEL--THE ONLY PARAMETERS.
     */
    private Matrix edgeCoef() {
        if (edgeCoef != null) {
            return edgeCoef;
        }

        List<Node> variableNodes = this.getVariableNodes();

        Matrix edgeCoef = new Matrix(variableNodes.size(), variableNodes.size());

        for (Edge edge : edgeParameters.keySet()) {
            if (Edges.isBidirectedEdge(edge)) {
                continue;
            }

            Node a = edge.getNode1();
            Node b = edge.getNode2();

            int aindex = variableNodes.indexOf(a);
            int bindex = variableNodes.indexOf(b);

            double coef = edgeParameters.get(edge);

            edgeCoef.set(aindex, bindex, coef);
        }

        this.edgeCoef = edgeCoef;
        return edgeCoef;
    }

    /**
     * @return For compatibility only. Returns the variable means of the model. These are always
     * zero, since this is a standardized model. THESE ARE ALSO NOT PARAMETERS OF THE MODEL. ONLY THE
     * COEFFICIENTS ARE PARAMETERS.
     */
    public double[] means() {
        return new double[semPm.getVariableNodes().size()];
    }

    /**
     * A convenience method, in case we want to change out mind about how to simulate. For instance,
     * it's unclear yet whether we can allow nongaussian errors, so we don't know yet whether the
     * reduced form method is needed.
     *
     * @param sampleSize      The sample size of the desired data set.
     * @param latentDataSaved True if latent variables should be included in the data set.
     * @return This returns a standardized data set simulated from the model, using the reduced form
     * method.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        return this.simulateDataReducedForm(sampleSize, latentDataSaved);
    }

    @Override
    public DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved) {
        RandomUtil random = RandomUtil.getInstance();
        long _seed = random.getSeed();
        random.setSeed(seed);
        DataSet dataSet = this.simulateData(sampleSize, latentDataSaved);
        random.revertSeed(_seed);
        return dataSet;
    }

    public DataSet simulateDataReducedForm(int sampleSize, boolean latentDataSaved) {
        edgeCoef = null;
        errorCovar = null;
        errorVariances = null;

        int numVars = this.getVariableNodes().size();

        // Calculate inv(I - edgeCoefC)
        Matrix B = this.edgeCoef().transpose();
        Matrix iMinusBInv = TetradAlgebra.identity(B.rows()).minus(B).inverse();

        // Pick error values e, for each calculate inv * e.
        Matrix sim = new Matrix(sampleSize, numVars);

        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            Vector e = new Vector(this.edgeCoef().columns());

            for (int i = 0; i < e.size(); i++) {
                e.set(i, RandomUtil.getInstance().nextNormal(0, sqrt(this.errCovar(this.errorVariances(), false).get(i, i))));
            }

            // Step 3. Calculate the new rows in the data.
            Vector sample = iMinusBInv.times(e);
            sim.assignRow(row, sample);

            for (int col = 0; col < sample.size(); col++) {
                double value = sim.get(row, col);
                sim.set(row, col, value);
            }
        }

        List<Node> continuousVars = new ArrayList<>();

        for (Node node : this.getVariableNodes()) {
            ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        DataSet fullDataSet = new BoxDataSet(new DoubleDataBox(sim.toArray()), continuousVars);

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    /**
     * @return a copy of the implied covariance matrix over all the variables.
     */
    public Matrix getImplCovar() {
        return this.implCovar();
    }

    /**
     * @return a copy of the implied covariance matrix over the measured
     * variables only.
     */
    public Matrix getImplCovarMeas() {
        return this.implCovarMeas().copy();
    }

    //========================================PRIVATE METHODS==========================================//

    /**
     * @return Returns the error covariance matrix of the model. i.e. [a][b] is the covariance of E_a and E_b,
     * with [a][a] of course being the variance of E_a. THESE ARE NOT PARAMETERS OF THE MODEL; THEY ARE
     * CALCULATED. Note that elements of this matrix may be Double.NaN; this indicates that these
     * elements cannot be calculated.
     */
    private Matrix errCovar(Map<Node, Double> errorVariances, boolean recalculate) {
        if (!recalculate && errorCovar != null) {
            return errorCovar;
        }

        List<Node> variableNodes = this.getVariableNodes();
        List<Node> errorNodes = new ArrayList<>();

        for (Node node : variableNodes) {
            errorNodes.add(semGraph.getExogenous(node));
        }

        Matrix errorCovar = new Matrix(errorVariances.size(), errorVariances.size());

        for (int index = 0; index < errorNodes.size(); index++) {
            Node error = errorNodes.get(index);
            double variance = this.getErrorVariance(error);
            errorCovar.set(index, index, variance);
        }

        for (int index1 = 0; index1 < errorNodes.size(); index1++) {
            for (int index2 = 0; index2 < errorNodes.size(); index2++) {
                Node error1 = errorNodes.get(index1);
                Node error2 = errorNodes.get(index2);
                Edge edge = semGraph.getEdge(error1, error2);

                if (edge != null && Edges.isBidirectedEdge(edge)) {
                    double covariance = this.getErrorCovariance(error1, error2);
                    errorCovar.set(index1, index2, covariance);
                }
            }
        }

        this.errorCovar = errorCovar;

        return errorCovar;
    }

    private Matrix implCovar() {
        this.computeImpliedCovar();
        return implCovar;
    }

    private Matrix implCovarMeas() {
        this.computeImpliedCovar();
        return implCovarMeas;
    }

    /**
     * Computes the implied covariance matrices of the Sem. There are two:
     * <code>implCovar </code> contains the covariances of all the variables and
     * <code>implCovarMeas</code> contains covariance for the measured variables
     * only.
     */
    private void computeImpliedCovar() {
        Matrix edgeCoefT = this.edgeCoef().transpose();

        // Note. Since the sizes of the temp matrices in this calculation
        // never change, we ought to be able to reuse them.
        implCovar = MatrixUtils.impliedCovar(edgeCoefT, this.errCovar(this.errorVariances(), true));

        // Submatrix of implied covar for measured vars only.
        int size = this.getMeasuredNodes().size();
        implCovarMeas = new Matrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Node iNode = this.getMeasuredNodes().get(i);
                Node jNode = this.getMeasuredNodes().get(j);

                int _i = this.getVariableNodes().indexOf(iNode);
                int _j = this.getVariableNodes().indexOf(jNode);

                implCovarMeas.set(i, j, implCovar.get(_i, _j));
            }
        }
    }

    /**
     * The list of measured nodes for the semPm. (Unmodifiable.)
     */
    public List<Node> getMeasuredNodes() {
        return this.getSemPm().getMeasuredNodes();
    }


    public List<Node> getErrorNodes() {
        List<Node> errorNodes = new ArrayList<>();

        for (Node node : this.getVariableNodes()) {
            errorNodes.add(semGraph.getExogenous(node));
        }

        return errorNodes;
    }

    /**
     * @return a copy of the SEM PM.
     */
    public SemPm getSemPm() {
        return new SemPm(semPm);
    }

    //-------------------------------------------PUBLIC CLASSES--------------------------------------------//

    /**
     * Stores a coefficient range--i.e. the edge and coefficient value for which the range is needed,
     * plus the low and high ends of the range to which the coefficient value may be adjusted.
     *
     * @author Joseph Ramsey
     */
    public static final class ParameterRange implements TetradSerializable {
        static final long serialVersionUID = 23L;

        private final Edge edge;
        private final double coef;
        private final double low;
        private final double high;

        public ParameterRange(Edge edge, double coef, double low, double high) {
            this.edge = edge;
            this.coef = coef;
            this.low = low;
            this.high = high;
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         */
        public static ParameterRange serializableInstance() {
            return new ParameterRange(Edge.serializableInstance(), 1.0, 1.0, 1.0);
        }

        public Edge getEdge() {
            return edge;
        }

        public double getCoef() {
            return coef;
        }

        public double getLow() {
            return low;
        }

        public double getHigh() {
            return high;
        }

        public String toString() {

            return "\n\nRange for " + edge +
                    "\nCurrent value = " + coef +
                    "\nLow end of range = " + low +
                    "\nHigh end of range = " + high;
        }
    }

    //-------------------------------------------PRIVATE METHODS-------------------------------------------//

    private boolean paramInBounds(Edge edge, double newValue) {
        edgeParameters.put(edge, newValue);
        Map<Node, Double> errorVariances = new HashMap<>();
        for (Node node : semPm.getVariableNodes()) {
            Node error = semGraph.getExogenous(node);
            double d2 = this.calculateErrorVarianceFromParams(error);
            if (Double.isNaN(d2)) {
                return false;
            }

            errorVariances.put(error, d2);
        }

        return MatrixUtils.isPositiveDefinite(this.errCovar(errorVariances, true));
    }

    /**
     * Calculates the error variance for the given error node, given all of the coefficient values in the model.
     *
     * @param error An error term in the model--i.e. a variable with NodeType.ERROR.
     * @return The value of the error variance, or Double.NaN is the value is undefined.
     */
    private double calculateErrorVarianceFromParams(Node error) {
        error = semGraph.getNode(error.getName());

        Node child = semGraph.getChildren(error).get(0);
        List<Node> parents = semGraph.getParents(child);

        double otherVariance = 0;

        for (Node parent : parents) {
            if (parent == error) continue;
            double coef = this.getEdgeCoef(parent, child);
            otherVariance += coef * coef;
        }

        if (parents.size() >= 2) {
            ChoiceGenerator gen = new ChoiceGenerator(parents.size(), 2);
            int[] indices;

            while ((indices = gen.next()) != null) {
                Node node1 = parents.get(indices[0]);
                Node node2 = parents.get(indices[1]);

                double coef1;
                double coef2;

                if (node1.getNodeType() != NodeType.ERROR) {
                    coef1 = this.getEdgeCoef(node1, child);
                } else {
                    coef1 = 1;
                }

                if (node2.getNodeType() != NodeType.ERROR) {
                    coef2 = this.getEdgeCoef(node2, child);
                } else {
                    coef2 = 1;
                }

                List<List<Node>> treks = GraphUtils.treksIncludingBidirected(semGraph, node1, node2);

                double cov = 0.0;

                for (List<Node> trek : treks) {
                    double product = 1.0;

                    for (int i = 1; i < trek.size(); i++) {
                        Node _node1 = trek.get(i - 1);
                        Node _node2 = trek.get(i);

                        Edge edge = semGraph.getEdge(_node1, _node2);
                        double factor;

                        if (Edges.isBidirectedEdge(edge)) {
                            factor = edgeParameters.get(edge);
                        } else if (!edgeParameters.containsKey(edge)) {
                            factor = 1;
                        } else if (semGraph.isParentOf(_node1, _node2)) {
                            factor = this.getEdgeCoef(_node1, _node2);
                        } else {
                            factor = this.getEdgeCoef(_node2, _node1);
                        }

                        product *= factor;
                    }

                    cov += product;
                }

                otherVariance += 2 * coef1 * coef2 * cov;
            }
        }

        return 1.0 - otherVariance <= 0 ? Double.NaN : 1.0 - otherVariance;
    }
}























