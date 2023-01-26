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

package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSSDC;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * Test the degenerate Gaussian score.
 *
 * @author bandrews
 */
public class TestBoss {
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_RUNS, 10);
        parameters.set(Params.DIFFERENT_GRAPHS, true);
        parameters.set(Params.NUM_MEASURES, 60);
        parameters.set(Params.AVG_DEGREE, 6);
        parameters.set(Params.SAMPLE_SIZE, 1000);
        parameters.set(Params.COEF_LOW, 0);
        parameters.set(Params.COEF_HIGH, 1);

        parameters.set(Params.BOSS_ALG, 1);

        parameters.set(Params.PENALTY_DISCOUNT, 2);
        parameters.set(Params.SEM_BIC_STRUCTURE_PRIOR, 0);
        parameters.set(Params.ALPHA, 1e-2);

        parameters.set("verbose", false);

        Statistics statistics = new Statistics();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new BOSS(new FisherZ(), new SemBicScore()));
        algorithms.add(new BOSSDC(new SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(true);

        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}




