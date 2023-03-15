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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Modifies the UniformGraphGenerator to generate random DAGs (not assuming
 * connectivity) with number of edges in given range, min to max. Constraints on
 * connectivity are removed. Original docs follow.
 * <p>
 * Generates random DAGs uniformly with certain classes of DAGs using variants
 * of Markov chain algorithm by Malancon, Dutour, and Philippe. Pieces of the
 * infrastructure of the algorithm are adapted from the the BNGenerator class by
 * Jaime Shinsuke Ide jaime.ide@poli.usp.br, released under the GNU General
 * Public License, for which the following statement is being included as part
 * of the license agreement:
 * <p>
 * "The BNGenerator distribution is free software; you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation (either version 2 of the License
 * or, at your option, any later version), provided that this notice and the
 * name of the author appear in all copies. "If you're using the software,
 * please notify jaime.ide@poli.usp.br so that you can receive updates and
 * patches. BNGenerator is distributed "as is", in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU
 * General Public License along with the BNGenerator distribution. If not, write
 * to the Free Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139,
 * USA."
 *
 * @author Joseph Ramsey
 */
public final class GraphGeneratorRandomNumEdges {
    private static final int ANY_DAG = 0;

    /**
     * Indicates the structural assumption. May be ANY_DAG, CONNECTED_DAG.
     */
    private final int structure;

    /**
     * The number of nodes in a graph. The default is 4.
     */
    private int numNodes;

    /**
     * The maximum number of edges in the graph. The default is the number of
     * nodes minus 1.
     */
    private int maxEdges;

    /**
     * The minimum number of edges in the graph. The default is 0. If a
     */
    private int minEdges;

    /**
     * The number of iterations for the Markov chain process.
     */
    private int numIterations;

    /**
     * Matrix of parents for each node. parentMatrix[i][0] indicates the number
     * of parents; parentMatrix[i][k] represents the (k-1)'th parent, k =
     * 1...max.
     */
    private int[][] parentMatrix;

    /**
     * Matrix of parents for each node. childMatrix[i][0] indicates the number
     * of parents; childMatrix[i][k] represents the (k-1)'th child, k =
     * 1...max.
     */
    private int[][] childMatrix;

    /**
     * Parent of random edge. 0 is the default parent node.
     */
    private int randomParent;

    /**
     * Child of random edge. 0 is the default child node.
     */
    private int randomChild = 1;

    //===============================CONSTRUCTORS==========================//

    /**
     * Constructs a random graph generator for the given structure.
     *
     * @param structure One of ANY_DAG, POLYTREE, or CONNECTED_DAG.
     */
    public GraphGeneratorRandomNumEdges(int structure) {
        if (structure != GraphGeneratorRandomNumEdges.ANY_DAG) {
            throw new IllegalArgumentException("Unrecognized structure.");
        }

        this.structure = structure;
        this.numNodes = 4;
        this.minEdges = 0;
        this.maxEdges = this.numNodes - 1;

        // Determining the number of iterations for the chain to converge is a
        // difficult task. This value follows the DagAlea (see Melancon;Bousque,
        // 2000) suggestion, and we verified that this number is satisfatory. (Ide.)
        this.numIterations = 6 * this.numNodes * this.numNodes;
    }

    //===============================PUBLIC METHODS========================//

    private int getNumNodes() {
        return this.numNodes;
    }

    /**
     * Sets the number of nodes and resets all of the other parameters to
     * default values accordingly.
     *
     * @param numNodes Must be an integer greater than or equal to 4.
     */
    public void setNumNodes(int numNodes) {
        if (numNodes < 4) {
            throw new IllegalArgumentException("Number of nodes must be >= 4.");
        }

        this.numNodes = numNodes;
        this.maxEdges = numNodes - 1;
        this.numIterations = 6 * numNodes * numNodes;
        this.parentMatrix = null;
        this.childMatrix = null;
    }

    private int getMaxEdges() {
        return this.maxEdges;
    }

    private int getNumIterations() {
        return this.numIterations;
    }

    private int getStructure() {
        return this.structure;
    }


    private int getMinEdges() {
        return this.minEdges;
    }

    public void setMinEdges(int minEdges) {
        if (minEdges < 0) {
            throw new IllegalArgumentException("Min edges must be >= 0.");
        }

        if (minEdges >= this.maxEdges - 2) {
            throw new IllegalArgumentException(
                    "Min edges must be < max edges - 1.");
        }

        this.minEdges = minEdges;
    }

    private int getMaxPossibleEdges() {
        return getNumNodes() * (getNumNodes() - 1) / 2;
    }

    public void setMaxEdges(int maxEdges) {
        if (maxEdges < 1) {
            throw new IllegalArgumentException("Max edges must be >= 1.");
        }

        if (maxEdges <= getMinEdges() + 2) {
            throw new IllegalArgumentException(
                    "Max edgs must be > max edges - 2.");
        }

        if (maxEdges > getMaxPossibleEdges()) {
            maxEdges = getMaxPossibleEdges();
        }

        this.maxEdges = maxEdges;
    }

    public void generate() {
        if (GraphGeneratorRandomNumEdges.ANY_DAG == getStructure()) {
            generateArbitraryDag();
        } else {
            throw new IllegalStateException("Unknown structure type.");
        }
    }

    public Dag getDag() {
        Dag dag = new Dag();

        List<Node> nodes = new ArrayList<>();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(0);

        int numDigits = (int) FastMath.ceil(FastMath.log(this.numNodes) / FastMath.log(10.0));
        nf.setMinimumIntegerDigits(numDigits);

        for (int i = 1; i <= getNumNodes(); i++) {
            GraphNode node = new GraphNode("X" + nf.format(i));
            dag.addNode(node);
            nodes.add(node);
        }

        for (int i = 0; i < getNumNodes(); i++) {
            Node child = nodes.get(i);

            if (this.parentMatrix[i][0] != 1) {
                for (int j = 1; j < this.parentMatrix[i][0]; j++) {
                    Node parent = nodes.get(this.parentMatrix[i][j]);
                    dag.addDirectedEdge(parent, child);
                }
            }
        }

        LayoutUtil.circleLayout(dag, 200, 200, 150);
        return dag;
    }

    public String toString() {
        return "\nStructural information for generated graph:" +
                "\n\tNumber of nodes:" + getNumNodes() +
                "\n\tNumber of transitions between samples:" + getNumIterations();
    }

    //================================PRIVATE METHODS======================//

    private void generateArbitraryDag() {
        initializeGraphAsEmpty();

        int numEdges = 0;

        for (int i = 0; i < getNumIterations(); i++) {
            sampleEdge();

            if (edgeExists()) {
                if (numEdges > getMinEdges()) {
                    removeEdge();
                    numEdges--;
                }
            } else {
                if ((numEdges < getMaxEdges() && isAcyclic())) {
                    addEdge();
                    numEdges++;
                }
            }
        }
    }

    /**
     * @return true if the edge parent-->child exists in the graph.
     */
    private boolean edgeExists() {
        for (int i = 1; i < this.parentMatrix[this.randomChild][0]; i++) {
            if (this.parentMatrix[this.randomChild][i] == this.randomParent) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if the graph is still acyclic after the last edge was added.
     * This method only works after adding an edge, not after removing an edge.
     */
    private boolean isAcyclic() {
        boolean[] visited = new boolean[getNumNodes()];
        boolean noCycle = true;
        int[] list = new int[getNumNodes() + 1];
        int index = 0;
        int lastIndex = 1;
        list[0] = this.randomParent;
        visited[this.randomParent] = true;
        while (index < lastIndex && noCycle) {
            int currentNode = list[index];
            int i = 1;

            // verify parents of getModel node
            while ((i < this.parentMatrix[currentNode][0]) && noCycle) {
                if (!visited[this.parentMatrix[currentNode][i]]) {
                    if (this.parentMatrix[currentNode][i] != this.randomChild) {
                        list[lastIndex] = this.parentMatrix[currentNode][i];
                        lastIndex++;
                    } else {
                        noCycle = false;
                    }
                    visited[this.parentMatrix[currentNode][i]] = true;
                } // end of if(visited)
                i++;
            }
            index++;
        }
        //System.out.println("\tnoCycle:"+noCycle);
        return noCycle;
    }

    /**
     * Initializes the graph to have no edges.
     */
    private void initializeGraphAsEmpty() {
        int max = getNumNodes();

        this.parentMatrix = new int[getNumNodes()][max];
        this.childMatrix = new int[getNumNodes()][max];

        for (int i = 0; i < getNumNodes(); i++) {
            this.parentMatrix[i][0] = 1; //set first node
            this.childMatrix[i][0] = 1;
        }

        for (int i = 0; i < getNumNodes(); i++) {
            for (int j = 1; j < max; j++) {
                this.parentMatrix[i][j] = -5; //set first node
                this.childMatrix[i][j] = -5;
            }
        }
    }

    /**
     * Sets randomParent-->randomChild to a random edge, chosen uniformly.
     */
    private void sampleEdge() {
        int rand = RandomUtil.getInstance().nextInt(
                getNumNodes() * (getNumNodes() - 1));
        this.randomParent = rand / (getNumNodes() - 1);
        int rest = rand - this.randomParent * (getNumNodes() - 1);
        if (rest >= this.randomParent) {
            this.randomChild = rest + 1;
        } else {
            this.randomChild = rest;
        }
    }

    /**
     * Adds the edge randomParent-->randomChild to the graph.
     */
    private void addEdge() {
        this.childMatrix[this.randomParent][this.childMatrix[this.randomParent][0]] = this.randomChild;
        this.childMatrix[this.randomParent][0]++;
        this.parentMatrix[this.randomChild][this.parentMatrix[this.randomChild][0]] = this.randomParent;
        this.parentMatrix[this.randomChild][0]++;
    }

    /**
     * Removes the edge randomParent-->randomChild from the graph.
     */
    private void removeEdge() {
        boolean go = true;
        int lastNode;
        int proxNode;
        int atualNode;
        if ((this.parentMatrix[this.randomChild][0] != 1) &&
                (this.childMatrix[this.randomParent][0] != 1)) {
            lastNode =
                    this.parentMatrix[this.randomChild][this.parentMatrix[this.randomChild][0] - 1];
            for (int i = (this.parentMatrix[this.randomChild][0] - 1); (i > 0 && go); i--) { // remove element from parentMatrix
                atualNode = this.parentMatrix[this.randomChild][i];
                if (atualNode != this.randomParent) {
                    proxNode = atualNode;
                    this.parentMatrix[this.randomChild][i] = lastNode;
                    lastNode = proxNode;
                } else {
                    this.parentMatrix[this.randomChild][i] = lastNode;
                    go = false;
                }
            }
            if ((this.childMatrix[this.randomParent][0] != 1) &&
                    (this.childMatrix[this.randomParent][0] != 1)) {
                lastNode = this.childMatrix[this.randomParent][
                        this.childMatrix[this.randomParent][0] - 1];
                go = true;
                for (int i = (this.childMatrix[this.randomParent][0] - 1); (i > 0 &&
                        go); i--) { // remove element from childMatrix
                    atualNode = this.childMatrix[this.randomParent][i];
                    if (atualNode != this.randomChild) {
                        proxNode = atualNode;
                        this.childMatrix[this.randomParent][i] = lastNode;
                        lastNode = proxNode;
                    } else {
                        this.childMatrix[this.randomParent][i] = lastNode;
                        go = false;
                    }
                } // end of for
            }
            this.childMatrix[this.randomParent][(this.childMatrix[this.randomParent][0] - 1)] = -4;
            this.childMatrix[this.randomParent][0]--;
            this.parentMatrix[this.randomChild][(this.parentMatrix[this.randomChild][0] - 1)] = -4;
            this.parentMatrix[this.randomChild][0]--;
        }
    }
}






