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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianScore implements Score {

    private final DataSet dataSet;

    // The variables of the dataset.
    private final List<Node> variables;

    // Likelihood function
    private final ConditionalGaussianLikelihood likelihood;

    private double penaltyDiscount;
    private int numCategoriesToDiscretize = 3;
    private double structurePrior = 0;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianScore(DataSet dataSet, double penaltyDiscount, boolean discretize) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;

        this.likelihood = new ConditionalGaussianLikelihood(dataSet);

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setPenaltyDiscount(penaltyDiscount);
        this.likelihood.setDiscretize(discretize);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        List<Integer> rows = getRows(i, parents);
        this.likelihood.setRows(rows);

        ConditionalGaussianLikelihood.Ret ret = this.likelihood.getLikelihood(i, parents);

        double lik = ret.getLik();
        int k = ret.getDof();

        double score = 2.0 * (lik + getStructurePrior(parents)) - getPenaltyDiscount() * k * FastMath.log(rows.size());

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return score;
        }
    }

    private List<Integer> getRows(int i, int[] parents) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            if (this.variables.get(i) instanceof DiscreteVariable) {
                if (this.dataSet.getInt(k, i) == -99) continue;
            } else if (this.variables.get(i) instanceof ContinuousVariable) {
                this.dataSet.getInt(k, i);
            }

            for (int p : parents) {
                if (this.variables.get(i) instanceof DiscreteVariable) {
                    if (this.dataSet.getInt(k, p) == -99) continue K;
                } else if (this.variables.get(i) instanceof ContinuousVariable) {
                    this.dataSet.getInt(k, p);
                }
            }

            rows.add(k);
        }

        return rows;
    }

    private double getStructurePrior(int[] parents) {
        if (this.structurePrior <= 0) {
            return 0;
        } else {
            int k = parents.length;
            double n = this.dataSet.getNumColumns() - 1;
            double p = this.structurePrior / n;
            return k * FastMath.log(p) + (n - k) * FastMath.log(1.0 - p);
        }
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
        return "Conditional Gaussian Score Penalty " + nf.format(this.penaltyDiscount);
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }
}



