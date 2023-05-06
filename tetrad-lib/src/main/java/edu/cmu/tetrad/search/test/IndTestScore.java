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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface implemented by classes that do conditional independence testing. These classes are capable of serving as
 * conditional independence "oracles" for constraint-based searches.
 *
 * @author Don Crimbchin (djc2@andrew.cmu.edu)
 * @author josephramsey
 */
public class IndTestScore implements IndependenceTest {

    private final Score score;
    private final List<Node> variables;
    private double bump = Double.NaN;
    private final DataModel data;
    private boolean verbose;

    public IndTestScore(Score score) {
        this(score, null);
    }

    public IndTestScore(Score score, DataModel data) {
        if (score == null) throw new NullPointerException();
        this.score = score;
        this.variables = score.getVariables();
        this.data = data;
    }

    /**
     * @return an Independence test for a subset of the variables.
     */
    public IndTestScore indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public IndependenceResult checkIndependence(Node x, Node y, List<Node> z) {
        List<Node> z1 = new ArrayList<>(z);

        if (determines(z1, x)) new IndependenceResult(new IndependenceFact(x, y, z), false, getPValue());
        ;
        if (determines(z1, y)) new IndependenceResult(new IndependenceFact(x, y, z), false, getPValue());
        ;

        double v = this.score.localScoreDiff(this.variables.indexOf(x), this.variables.indexOf(y), varIndices(z));
        this.bump = v;

        boolean independent = v <= 0;

        if (this.verbose) {
            if (independent) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFact(x, y, z) + " score = " + nf.format(bump));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, getPValue());
    }

    private int[] varIndices(List<Node> z) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = this.variables.indexOf(z.get(i));
        }

        return indices;
    }

    /**
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return this.bump;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable by the given name.
     */
    public Node getVariable(String name) {
        for (Node node : this.variables) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }

    /**
     * @return true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        return this.score.determines(z, y);
    }

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return -1;
    }

    /**
     * Sets the significance level.
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
    }

    /**
     * @return The data model for the independence test.
     */
    public DataModel getData() {
        return this.data;
    }

    /**
     * Returns the covariance matrix.
     * @return This matrix.
     */
    public ICovarianceMatrix getCov() {
        return ((SemBicScore) this.score).getCovariances();
    }

    /**
     * @throws UnsupportedOperationException Not implemented.
     */
    public List<DataSet> getDataSets() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the sample size.
     * @return This size.
     */
    public int getSampleSize() {
        return this.score.getSampleSize();
    }

    /**
     * @return A score that is higher with more likely models.
     */
    public double getScore() {
        return this.bump;
    }

    /**
     * Returns the score that this test wraps.
     * @return This score
     * @see Score
     */
    public Score getWrappedScore() {
        return this.score;
    }

    /**
     * Returns true if verbose ouput should be printed.
     * @return True if so.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     * @param verbose True if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a String representation of this test.
     * @return This string.
     */
    @Override
    public String toString() {
        return this.score.toString() + " Interpreted as a Test";
    }
}



