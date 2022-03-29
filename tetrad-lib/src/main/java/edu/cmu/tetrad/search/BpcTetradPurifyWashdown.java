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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Implements a really simple idea for building pure clusters, just using the Purify algorithm.
 *
 * @author Joseph Ramsey
 */
public class BpcTetradPurifyWashdown {
    private DataSet dataSet;
    private ICovarianceMatrix cov;
    private final List<Node> variables;
    private final TetradTest test;
    private double alpha;
    private static final int MAX_CLIQUE_TRIALS = 50;
    private final IndependenceTest indTest;
    private final boolean depthOne = false;
    private EdgeListGraph depthOneGraph;

    public BpcTetradPurifyWashdown(ICovarianceMatrix cov, TestType testType, double alpha) {
        this.cov = cov;
        variables = cov.getVariables();
        test = new ContinuousTetradTest(cov, testType, alpha);
        this.alpha = alpha;
        indTest = new IndTestFisherZ(cov, alpha);
    }

    public BpcTetradPurifyWashdown(DataSet dataSet, TestType testType, double alpha) {
        this.dataSet = dataSet;
        variables = dataSet.getVariables();
        test = new ContinuousTetradTest(dataSet, testType, alpha);
        indTest = new IndTestFisherZ(dataSet, alpha);
    }

    public Graph search() {
        IPurify purify = new PurifyTetradBased2(test);
        List<Node> variables = new ArrayList<>(this.variables);
        List<List<Node>> clustering = new ArrayList<>();
        List<Node> disgards;
        List<Node> _disgards;

        do {
            _disgards = this.calculateDisgards(clustering, variables);
            clustering.add(_disgards);
            clustering = purify.purify(clustering);
            disgards = this.calculateDisgards(clustering, variables);
        } while (!new HashSet<>(disgards).equals(new HashSet<>(_disgards)));

        Graph graph = new EdgeListGraph();

        for (List<Node> cluster : new ArrayList<>(clustering)) {
            if (cluster.size() < 3) {
                clustering.remove(cluster);
            }
        }

        for (int i = 0; i < clustering.size(); i++) {
            List<Node> cluster = clustering.get(i);
            Node latent = new GraphNode("_L" + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);

            for (Node node : cluster) {
                graph.addNode(node);
                graph.addDirectedEdge(latent, node);
            }
        }

        return graph;

    }

    private List<Node> calculateDisgards(List<List<Node>> clustering, List<Node> variables) {
        List<Node> disgards = new ArrayList<>();

        NODE:
        for (Node node : variables) {
            for (List<Node> cluster : clustering) {
                if (cluster.contains(node)) {
                    continue NODE;
                }
            }

            disgards.add(node);
        }

        return disgards;
    }


}




