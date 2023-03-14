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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianOtherScore implements Score {

    private final DataSet dataSet;

    // The variables of the continuousData set.
    private final List<Node> variables;

    // Likelihood function
    private final ConditionalGaussianOtherLikelihood likelihood;

    private double penaltyDiscount = 1;
    private int numCategoriesToDiscretize = 3;
    private final double sp;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianOtherScore(DataSet dataSet, double sp) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sp = sp;

        this.likelihood = new ConditionalGaussianOtherLikelihood(dataSet);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     *
     * @return the score, or NaN if the score can't be calculated.
     */
    public double localScore(int i, int... parents) {
        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setPenaltyDiscount(this.penaltyDiscount);

        ConditionalGaussianOtherLikelihood.Ret ret = this.likelihood.getLikelihood(i, parents);

        int N = this.dataSet.getNumRows();
        double lik = ret.getLik();
        int k = ret.getDof();


        double strucPrior = getStructurePrior(parents);
        if (strucPrior > 0) {
            strucPrior = -2 * k * strucPrior;
        }

        double score = 2.0 * lik - getPenaltyDiscount() * k * FastMath.log(N) + strucPrior;

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    private double getStructurePrior(int[] parents) {
        if (this.sp < 0) {
            return getEBICprior();
        } else if (this.sp == 0) {
            return 0;
        } else {
            int i = parents.length;
            int c = this.dataSet.getNumColumns() - 1;
            double p = this.sp / (double) c;
            return i * FastMath.log(p) + (c - i) * FastMath.log(1.0 - p);
        }
    }

    private double getEBICprior() {

        double n = this.dataSet.getNumColumns();
        double gamma = -this.sp;
        return gamma * FastMath.log(n);

    }

    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }


    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */


    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.dataSet.getNumRows()));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Conditional Gaussian Other Score Penalty " + nf.format(this.penaltyDiscount);
    }

}



