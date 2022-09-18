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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the first sepset it comes to from among the extra sepsets or the adjacents of i or k,
 * or null if none is found.
 */
public class SepsetsTeyssier implements SepsetProducer {
    private final Graph graph;
    private final TeyssierScorer scorer;
    private final SepsetMap extraSepsets;
    private final int sepsetsDepth;

    public SepsetsTeyssier(Graph graph, TeyssierScorer scorer, SepsetMap extraSepsets, int sepsetsDepth) {
        this.graph = graph;
        this.scorer = scorer;
        this.extraSepsets = extraSepsets;
        this.sepsetsDepth = sepsetsDepth;
    }

    /**
     * Pick out the sepset from among adj(i) or adj(k) with the highest score value.
     */
    public List<Node> getSepset(Node i, Node k) {
        return getSepsetGreedy(i, k);
    }

    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        List<Node> set = getSepsetGreedy(i, k);
        return set != null && !set.contains(j);
    }

    public boolean isUnshieldedNoncollider(Node i, Node j, Node k) {
        List<Node> set = getSepsetGreedy(i, k);
        return set != null && set.contains(j);
    }

    private List<Node> getSepsetGreedy(Node i, Node k) {
        if (this.extraSepsets != null) {
            List<Node> v = this.extraSepsets.get(i, k);

            if (v != null) {
                return v;
            }
        }

        List<Node> adji = this.graph.getAdjacentNodes(i);
        List<Node> adjk = this.graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((this.sepsetsDepth == -1 ? 1000 : this.sepsetsDepth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);

                    if (isIndependent(i, k, v)) {
                        return v;
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);

                    if (isIndependent(i, k, v)) {
                        return v;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean isIndependent(Node a, Node b, List<Node> c) {
        List<Node> nodes = new ArrayList<>();
        nodes.addAll(c);
        nodes.add(a);
        nodes.add(b);

        Boss boss = new Boss(scorer);
        boss.setAlgType(Boss.AlgType.BOSS);
        boss.bestOrder(nodes);

//        this.scorer.score(nodes);
        boolean adjacent = this.scorer.adjacent(a, b);

//        System.out.println("Testing " + SearchLogUtils.independenceFact(a, b, c) + ": " + !adjacent);

        return !adjacent;
    }

    @Override
    public double getScore() {
        return 0;
    }

    @Override
    public List<Node> getVariables() {
        return this.scorer.getPi();
    }

    @Override
    public void setVerbose(boolean verbose) {
    }

    public Graph getDag() {
        return this.scorer.getGraph(false);
    }
}

