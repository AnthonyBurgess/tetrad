package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.*;

import static org.apache.commons.math3.util.FastMath.min;

public class RandomGraph {
    public static Graph randomDag(int numNodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomDag(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Dag randomDag(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return new Dag(randomGraph(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected));
    }

    public static Graph randomGraph(int numNodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    /**
     * Defaults to random forward graphs.
     */
    public static Graph randomGraph(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
    }

    public static Graph randomGraphUniform(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected, int numIterations) {
        int numNodes = nodes.size();

        if (numNodes == 0) {
            throw new IllegalArgumentException("NumNodes most be > 0: " + numNodes);
        }

        if (maxNumEdges < 0 || maxNumEdges > numNodes * (numNodes - 1)) {
            throw new IllegalArgumentException("NumEdges must be " + "at least 0 and at most (#nodes)(#nodes - 1) / 2: " + maxNumEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > numNodes) {
            throw new IllegalArgumentException("Max # latent confounders must be " + "at least 0 and at most the number of nodes: " + numLatentConfounders);
        }

        for (Node node : nodes) {
            node.setNodeType(NodeType.MEASURED);
        }

        UniformGraphGenerator generator;

        if (connected) {
            generator = new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        } else {
            generator = new UniformGraphGenerator(UniformGraphGenerator.ANY_DAG);
        }

        generator.setNumNodes(numNodes);
        generator.setMaxEdges(maxNumEdges);
        generator.setMaxDegree(maxDegree);
        generator.setMaxInDegree(maxIndegree);
        generator.setMaxOutDegree(maxOutdegree);
        generator.setNumIterations(numIterations);
        generator.generate();
        Graph dag = generator.getDag(nodes);

        // Create a list of nodes. Add the nodes in the list to the
        // dag. Arrange the nodes in a circle.
        fixLatents1(numLatentConfounders, dag);

        LayoutUtil.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    static List<Node> getCommonCauses(Graph dag) {
        List<Node> commonCauses = new ArrayList<>();
        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> children = dag.getChildren(node);

            if (children.size() >= 2) {
                commonCauses.add(node);
            }
        }

        return commonCauses;
    }

    public static Graph randomGraphRandomForwardEdges(int numNodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return randomGraphRandomForwardEdges(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected, boolean layoutAsCircle) {
        if (nodes.size() == 0) {
            throw new IllegalArgumentException("NumNodes most be > 0");
        }

        // Believe it or not ths is needed.
        long size = nodes.size();

        if (numEdges < 0 || numEdges > size * (size - 1)) {
            throw new IllegalArgumentException("numEdges must be " + "greater than 0 and <= (#nodes)(#nodes - 1) / 2: " + numEdges);
        }

        if (numLatentConfounders < 0 || numLatentConfounders > nodes.size()) {
            throw new IllegalArgumentException("MaxNumLatents must be " + "greater than 0 and less than the number of nodes: " + numLatentConfounders);
        }

        LinkedList<List<Integer>> allEdges = new LinkedList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                List<Integer> pair = new ArrayList<>(2);
                pair.add(i);
                pair.add(j);
                allEdges.add(pair);
            }
        }

        Graph dag;

        int numTriesForGraph = 0;

        do {
            dag = new EdgeListGraph(nodes);

            RandomUtil.shuffle(allEdges);

            while (!allEdges.isEmpty() && dag.getNumEdges() < numEdges) {
                List<Integer> e = allEdges.removeFirst();

                Node n1 = nodes.get(e.get(0));
                Node n2 = nodes.get(e.get(1));

                if (dag.getIndegree(n2) >= maxIndegree) {
                    continue;
                }

                if (dag.getOutdegree(n1) >= maxOutdegree) {
                    continue;
                }

                if (dag.getIndegree(n1) + dag.getOutdegree(n1) >= maxDegree) {
                    continue;
                }

                if (dag.getIndegree(n2) + dag.getOutdegree(n2) >= maxDegree) {
                    continue;
                }

                dag.addDirectedEdge(n1, n2);
            }
        } while (++numTriesForGraph < 1000 && connected && (new Paths(dag).connectedComponents().size() != 1));

        fixLatents4(numLatentConfounders, dag);

        if (layoutAsCircle) {
            LayoutUtil.circleLayout(dag, 200, 200, 150);
        }

        return dag;
    }

    public static Graph randomScaleFreeGraph(int numNodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return randomScaleFreeGraph(nodes, numLatentConfounders, alpha, beta, delta_in, delta_out);
    }

    private static Graph randomScaleFreeGraph(List<Node> _nodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {

        if (alpha + beta >= 1) {
            throw new IllegalArgumentException("For the Bollobas et al. algorithm," + "\napha + beta + gamma = 1, so alpha + beta must be < 1.");
        }

        RandomUtil.shuffle(_nodes);

        LinkedList<Node> nodes = new LinkedList<>();
        nodes.add(_nodes.get(0));

        Graph G = new EdgeListGraph(_nodes);

        if (alpha <= 0) {
            throw new IllegalArgumentException("alpha must be > 0.");
        }
        if (beta <= 0) {
            throw new IllegalArgumentException("beta must be > 0.");
        }

        double gamma = 1.0 - alpha - beta;

        if (gamma <= 0) {
            throw new IllegalArgumentException("alpha + beta must be < 1.");
        }

        if (delta_in <= 0) {
            throw new IllegalArgumentException("delta_in must be > 0.");
        }
        if (delta_out <= 0) {
            throw new IllegalArgumentException("delta_out must be > 0.");
        }

        Map<Node, Set<Node>> parents = new HashMap<>();
        Map<Node, Set<Node>> children = new HashMap<>();
        parents.put(_nodes.get(0), new HashSet<>());
        children.put(_nodes.get(0), new HashSet<>());

        while (nodes.size() < _nodes.size()) {
            double r = RandomUtil.getInstance().nextDouble();
            int v, w;

            if (r < alpha) {
                v = nodes.size();
                w = chooseNode(indegrees(nodes, parents), delta_in);
                Node m = _nodes.get(v);
                nodes.addFirst(m);
                parents.put(m, new HashSet<>());
                children.put(m, new HashSet<>());
                v = 0;
                w++;
            } else if (r < alpha + beta) {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = chooseNode(indegrees(nodes, parents), delta_in);
                if (!(w > v)) {
                    continue;
                }
            } else {
                v = chooseNode(outdegrees(nodes, children), delta_out);
                w = nodes.size();
                Node m = _nodes.get(w);
                nodes.addLast(m);
                parents.put(m, new HashSet<>());
                children.put(m, new HashSet<>());
            }

            if (G.isAdjacentTo(nodes.get(v), nodes.get(w))) {
                continue;
            }

            G.addDirectedEdge(nodes.get(v), nodes.get(w));

            parents.get(nodes.get(w)).add(nodes.get(v));
            children.get(nodes.get(v)).add(nodes.get(w));
        }

        fixLatents1(numLatentConfounders, G);

        LayoutUtil.circleLayout(G, 200, 200, 150);

        return G;
    }

    private static int chooseNode(int[] distribution, double delta) {
        double cumsum = 0.0;
        double psum = sum(distribution) + delta * distribution.length;
        double r = RandomUtil.getInstance().nextDouble();

        for (int i = 0; i < distribution.length; i++) {
            cumsum += (distribution[i] + delta) / psum;

            if (r < cumsum) {
                return i;
            }
        }

        throw new IllegalArgumentException("Didn't pick a node.");
    }

    private static int sum(int[] distribution) {
        int sum = 0;
        for (int w : distribution) {
            sum += w;
        }
        return sum;
    }

    private static int[] indegrees(List<Node> nodes, Map<Node, Set<Node>> parents) {
        int[] indegrees = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            indegrees[i] = parents.get(nodes.get(i)).size();
        }

        return indegrees;
    }

    private static int[] outdegrees(List<Node> nodes, Map<Node, Set<Node>> children) {
        int[] outdegrees = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            outdegrees[i] = children.get(nodes.get(i)).size();
        }

        return outdegrees;
    }

    public static void fixLatents1(int numLatentConfounders, Graph graph) {
        List<Node> commonCauses = getCommonCauses(graph);
        int index = 0;

        while (index++ < numLatentConfounders) {
            if (commonCauses.size() == 0) {
                break;
            }
            int i = RandomUtil.getInstance().nextInt(commonCauses.size());
            Node node = commonCauses.get(i);
            node.setNodeType(NodeType.LATENT);
            commonCauses.remove(i);
        }
    }

    // JMO's method for fixing latents
    public static void fixLatents4(int numLatentConfounders, Graph graph) {
        if (numLatentConfounders == 0) {
            return;
        }

        List<Node> commonCausesAndEffects = getCommonCausesAndEffects(graph);
        int index = 0;

        while (index++ < numLatentConfounders) {
            if (commonCausesAndEffects.size() == 0) {
                index--;
                break;
            }
            int i = RandomUtil.getInstance().nextInt(commonCausesAndEffects.size());
            Node node = commonCausesAndEffects.get(i);
            node.setNodeType(NodeType.LATENT);
            commonCausesAndEffects.remove(i);
        }

        List<Node> nodes = graph.getNodes();
        while (index++ < numLatentConfounders) {
            int r = RandomUtil.getInstance().nextInt(nodes.size());
            if (nodes.get(r).getNodeType() == NodeType.LATENT) {
                index--;
            } else {
                nodes.get(r).setNodeType(NodeType.LATENT);
            }
        }
    }

    //Helper method for fixLatents4
    //Common effects refers to common effects with at least one child
    private static List<Node> getCommonCausesAndEffects(Graph dag) {
        List<Node> commonCausesAndEffects = new ArrayList<>();
        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> children = dag.getChildren(node);

            if (children.size() >= 2) {
                commonCausesAndEffects.add(node);
            } else {
                List<Node> parents = dag.getParents(node);
                if (parents.size() >= 2 && children.size() >= 1) {
                    commonCausesAndEffects.add(node);
                }
            }
        }

        return commonCausesAndEffects;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5 to the graph, then finally adding two
     * cycles.
     */
    public static Graph randomCyclicGraph2(int numNodes, int numEdges, int maxDegree) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);

        LOOP:
        while (graph.getEdges().size() < numEdges /*&& ++count1 < 100*/) {
//            int cycleSize = RandomUtil.getInstance().nextInt(2) + 4;
            int cycleSize = RandomUtil.getInstance().nextInt(3) + 3;

            // Pick that many nodes randomly
            List<Node> cycleNodes = new ArrayList<>();
            int count2 = -1;

            for (int i = 0; i < cycleSize; i++) {
                Node node = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));

                if (cycleNodes.contains(node)) {
                    i--;
                    ++count2;
                    if (count2 < 10) {
                        continue;
                    }
                }

                cycleNodes.add(node);
            }

            for (Node cycleNode : cycleNodes) {
                if (graph.getDegree(cycleNode) >= maxDegree) {
                    continue LOOP;
                }
            }

            Edge edge;

            // Make sure you won't create any two cycles (this will be done later, explicitly)
            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i + 1), cycleNodes.get(i));

                if (graph.containsEdge(edge)) {
                    continue LOOP;
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));

            if (graph.containsEdge(edge)) {
                continue;
            }

            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i), cycleNodes.get(i + 1));

                if (!graph.containsEdge(edge)) {
                    graph.addEdge(edge);

                    if (graph.getNumEdges() == numEdges) {
                        break LOOP;
                    }
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(cycleNodes.size() - 1), cycleNodes.get(0));

            if (!graph.containsEdge(edge)) {
                graph.addEdge(edge);

                if (graph.getNumEdges() == numEdges) {
                    break;
                }
            }
        }

        LayoutUtil.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5 to the graph, then finally adding two
     * cycles.
     */
    public static Graph randomCyclicGraph3(int numNodes, int numEdges, int maxDegree, double probCycle, double probTwoCycle) {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph graph = new EdgeListGraph(nodes);

        LOOP:
        while (graph.getEdges().size() < numEdges /*&& ++count1 < 100*/) {
//            int cycleSize = RandomUtil.getInstance().nextInt(2) + 4;
            int cycleSize = RandomUtil.getInstance().nextInt(3) + 3;

            // Pick that many nodes randomly
            List<Node> cycleNodes = new ArrayList<>();
            int count2 = -1;

            for (int i = 0; i < cycleSize; i++) {
                Node node = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));

                if (cycleNodes.contains(node)) {
                    i--;
                    ++count2;
                    if (count2 < 10) {
                        continue;
                    }
                }

                cycleNodes.add(node);
            }

            for (Node cycleNode : cycleNodes) {
                if (graph.getDegree(cycleNode) >= maxDegree) {
                    continue LOOP;
                }
            }

            Edge edge;

            // Make sure you won't create any two cycles (this will be done later, explicitly)
            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i + 1), cycleNodes.get(i));

                if (graph.containsEdge(edge)) {
                    continue LOOP;
                }
            }

            edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));

            if (graph.containsEdge(edge)) {
                continue;
            }

            for (int i = 0; i < cycleNodes.size() - 1; i++) {
                edge = Edges.directedEdge(cycleNodes.get(i), cycleNodes.get(i + 1));

                if (!graph.containsEdge(edge)) {
                    graph.addEdge(edge);

                    if (graph.getNumEdges() == numEdges) {
                        break LOOP;
                    }
                }
            }

            if (RandomUtil.getInstance().nextDouble() < probCycle) {
                edge = Edges.directedEdge(cycleNodes.get(cycleNodes.size() - 1), cycleNodes.get(0));
            } else {
                edge = Edges.directedEdge(cycleNodes.get(0), cycleNodes.get(cycleNodes.size() - 1));
            }

            if (!graph.containsEdge(edge)) {
                graph.addEdge(edge);

                if (graph.getNumEdges() == numEdges) {
                    break;
                }
            }
        }

        Set<Edge> edges = graph.getEdges();

        for (Edge edge : edges) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            if (RandomUtil.getInstance().nextDouble() < probTwoCycle) {
                graph.removeEdges(a, b);
                graph.addEdge(Edges.directedEdge(a, b));
                graph.addEdge(Edges.directedEdge(b, a));
            }
        }

        LayoutUtil.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    public static void addTwoCycles(Graph graph, int numTwoCycles) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        RandomUtil.shuffle(edges);

        for (int i = 0; i < min(numTwoCycles, edges.size()); i++) {
            Edge edge = edges.get(i);
            Edge reversed = Edges.directedEdge(edge.getNode2(), edge.getNode1());

            if (graph.containsEdge(reversed)) {
                i--;
                continue;
            }

            graph.addEdge(reversed);
        }
    }

    /**
     * Generates random DAGs uniformly with certain classes of DAGs using variants of Markov chain algorithm by
     * Malancon, Dutour, and Philippe. Pieces of the infrastructure of the algorithm are adapted from the the
     * BNGenerator class by Jaime Shinsuke Ide jaime.ide@poli.usp.br, released under the GNU General Public License, for
     * which the following statement is being included as part of the license agreement:
     * <p>
     * "The BNGenerator distribution is free software; you can redistribute it and/or modify it under the terms of the
     * GNU General Public License as published by the Free Software Foundation (either version 2 of the License or, at
     * your option, any later version), provided that this notice and the name of the author appear in all copies. "If
     * you're using the software, please notify jaime.ide@poli.usp.br so that you can receive updates and patches.
     * BNGenerator is distributed "as is", in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
     * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
     * for more details. You should have received a copy of the GNU General Public License along with the BNGenerator
     * distribution. If not, write to the Free Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA."
     *
     * @author josephramsey
     */
    public static final class UniformGraphGenerator {
        public static final int ANY_DAG = 0;
        public static final int CONNECTED_DAG = 1;

        /**
         * Indicates the structural assumption. May be ANY_DAG, CONNECTED_DAG.
         */
        private final int structure;

        /**
         * The number of nodes in a graph. The default is 4.
         */
        private int numNodes;

        /**
         * The maximum indegree for a node in a graph. The default is 3.
         */
        private int maxInDegree;

        /**
         * The maximum outdegree of a node in a graph. The defualt is 3.
         */
        private int maxOutDegree;

        /**
         * The maximum degree of a node in a graph. The default is the maximum number possible (the value -1 is used for
         * this).
         */
        private int maxDegree;

        /**
         * The maximum number of edges in the graph. The default is the number of nodes minus 1.
         */
        private int maxEdges;

        /**
         * The number of iterations for the Markov chain process.
         */
        private int numIterations;

        /**
         * Matrix of parents for each node. parentMatrix[i][0] indicates the number of parents; parentMatrix[i][k]
         * represents the (k-1)'th parent, k = 1...max.
         */
        private int[][] parentMatrix;

        /**
         * Matrix of parents for each node. childMatrix[i][0] indicates the number of parents; childMatrix[i][k]
         * represents the (k-1)'th child, k = 1...max.
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

        /**
         * The random source.
         */
        private final RandomUtil randomUtil = RandomUtil.getInstance();
        //    RandomUtil randomUtil = new SeededRandomUtil(23333342L);

        //===============================CONSTRUCTORS==========================//

        /**
         * Constructs a random graph generator for the given structure.
         *
         * @param structure One of ANY_DAG, POLYTREE, or CONNECTED_DAG.
         */
        public UniformGraphGenerator(int structure) {
            switch (structure) {
                case UniformGraphGenerator.ANY_DAG:
                case UniformGraphGenerator.CONNECTED_DAG:
                    break;
                default:
                    throw new IllegalArgumentException("Unrecognized structure.");
            }

            this.structure = structure;
            this.numNodes = 4;
            this.maxInDegree = 3;
            this.maxOutDegree = 3;
            this.maxDegree = 6;
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
         * Sets the number of nodes and resets all of the other parameters to default values accordingly.
         *
         * @param numNodes Must be an integer &gt;= 4.
         */
        public void setNumNodes(int numNodes) {
            if (numNodes < 1) {
                throw new IllegalArgumentException("Number of nodes must be >= 1.");
            }

            this.numNodes = numNodes;
            this.maxDegree = numNodes - 1;
            this.maxInDegree = numNodes - 1;
            this.maxOutDegree = numNodes - 1;
            this.maxEdges = numNodes - 1;
            this.numIterations = 6 * numNodes * numNodes;

            if (this.numIterations > 300000000) {
                this.numIterations = 300000000;
            }

            this.parentMatrix = null;
            this.childMatrix = null;
        }

        private int getMaxDegree() {
            return this.maxDegree;
        }

        /**
         * Sets the maximum degree of any nodes in the graph.
         *
         * @param maxDegree An integer between 3 and numNodes - 1, inclusively.
         */
        public void setMaxDegree(int maxDegree) {
            if (maxDegree < 3) {
                throw new IllegalArgumentException("Degree of nodes must be >= 3.");
            }

            this.maxDegree = maxDegree;
        }

        private int getMaxInDegree() {
            return this.maxInDegree;
        }

        public void setMaxInDegree(int maxInDegree) {
            if (UniformGraphGenerator.ANY_DAG == getStructure() && getMaxInDegree() < 0) {
                throw new IllegalArgumentException("Max indegree must be >= 1 " + "when generating DAGs without the assumption of " + "connectedness.");
            } else if (UniformGraphGenerator.CONNECTED_DAG == getStructure() && getMaxInDegree() < 2) {
                throw new IllegalArgumentException("Max indegree must be >= 2 " + "when generating DAGs under the assumption of " + "connectedness.");
            }

            this.maxInDegree = maxInDegree;
        }

        private int getMaxOutDegree() {
            return this.maxOutDegree;
        }

        public void setMaxOutDegree(int maxOutDegree) {
            if (UniformGraphGenerator.ANY_DAG == getStructure() && getMaxInDegree() < 1) {
                throw new IllegalArgumentException("Max indegree must be >= 1 " + "when generating DAGs without the assumption of " + "connectedness.");
            }

            if (UniformGraphGenerator.CONNECTED_DAG == getStructure() && getMaxInDegree() < 2) {
                throw new IllegalArgumentException("Max indegree must be >= 2 " + "when generating DAGs under the assumption of " + "connectedness.");
            }

            this.maxOutDegree = maxOutDegree;
        }

        private int getMaxEdges() {
            return this.maxEdges;
        }

        private int getMaxPossibleEdges() {
            return getNumNodes() * getMaxDegree() / 2;
        }

        public void setMaxEdges(int maxEdges) {
            if (maxEdges < 0) {
                throw new IllegalArgumentException("Max edges must be >= 0.");
            }

            if (maxEdges > getMaxPossibleEdges()) {
                maxEdges = getMaxPossibleEdges();
            }

            this.maxEdges = maxEdges;
        }

        private int getNumIterations() {
            return this.numIterations;
        }

        public void setNumIterations(int numIterations) {
            this.numIterations = numIterations;
        }

        private int getStructure() {
            return this.structure;
        }

        public void generate() {
            if (UniformGraphGenerator.ANY_DAG == getStructure()) {
                generateArbitraryDag();
            } else if (UniformGraphGenerator.CONNECTED_DAG == getStructure()) {
                generateConnectedDag();
            } else {
                throw new IllegalStateException("Unknown structure type.");
            }
        }

        public Graph getDag() {
            //System.out.println("Converting to DAG");

            List<Node> nodes = new ArrayList<>();
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(0);

            int numDigits = (int) FastMath.ceil(FastMath.log(this.numNodes) / FastMath.log(10.0));
            nf.setMinimumIntegerDigits(numDigits);
            nf.setGroupingUsed(false);


            for (int i = 1; i <= getNumNodes(); i++) {
                GraphNode node = new GraphNode("X" + nf.format(i));
                //            dag.addIndex(node);
                nodes.add(node);
            }

            return getDag(nodes);
        }

        public Graph getDag(List<Node> nodes) {
            if (nodes.size() != getNumNodes()) {
                throw new IllegalArgumentException("Only " + nodes.size() + " nodes were provided, but the " + "simulated graph has " + getNumNodes() + ".");
            }

            Graph dag = new EdgeListGraph(nodes);

            for (int i = 0; i < getNumNodes(); i++) {
                Node child = nodes.get(i);

                if (this.parentMatrix[i][0] != 1) {
                    for (int j = 1; j < this.parentMatrix[i][0]; j++) {
                        Node parent = nodes.get(this.parentMatrix[i][j]);
                        dag.addDirectedEdge(parent, child);
                        //                    System.out.println("Added " + dag.getEdge(parent, child));
                    }
                }
            }

            //        System.out.println("Arranging in circle.");
            LayoutUtil.circleLayout(dag, 200, 200, 150);

            //System.out.println("DAG conversion completed.");

            return dag;
        }

        public void printEdges() {
            System.out.println("Edges:");
            for (int i = 0; i < getNumNodes(); i++) {
                for (int j = 1; j < this.childMatrix[i][0]; j++) {
                    System.out.println("\t" + i + " --> " + this.childMatrix[i][j]);
                }
            }
        }

        public String toString() {
            return "\nStructural information for generated graph:" + "\n\tNumber of nodes:" + getNumNodes() + "\n\tMax degree for each node:" + getMaxDegree() + "\n\tMaximum number of incoming edges for each node:" + getMaxInDegree() + "\n\tMaximum number of outgoing edges for each node:" + getMaxOutDegree() + "\n\tMaximum total number of edges:" + getMaxEdges() + " of " + getNumNodes() * getMaxDegree() / 2 + " possibles" + "\n\tNumber of transitions between samples:" + getNumIterations();
        }

        //================================PRIVATE METHODS======================//

        private void generateArbitraryDag() {
            initializeGraphAsEmpty();

            if (getNumNodes() <= 1) {
                return;
            }

            int numEdges = 0;

            for (int i = 0; i < getNumIterations(); i++) {
                //            if (i % 10000000 == 0) System.out.println("..." + i);

                sampleEdge();

                if (edgeExists()) {
                    removeEdge();
                    numEdges--;
                } else {
                    if ((numEdges < getMaxEdges() && maxDegreeNotExceeded() && maxIndegreeNotExceeded() && maxOutdegreeNotExceeded() && isAcyclic())) {
                        addEdge();
                        numEdges++;
                    }
                }
            }
        }

        /**
         * This is the algorithm in Melancon and Philippe, "Generating connected acyclic digraphs uniformly at random"
         * (draft of March 25, 2004). In addition to acyclicity, some other conditions have been added in.
         */
        private void generateConnectedDag() {
            initializeGraphAsChain();

            if (getNumNodes() <= 1) {
                return;
            }

            int totalEdges = getNumNodes() - 1;

            while (isDisconnecting()) {
                sampleEdge();

                if (edgeExists()) {
                    continue;
                }

                if (isAcyclic() && maxDegreeNotExceeded()) {
                    addEdge();
                    totalEdges++;
                }
            }

            for (int i = 0; i < getNumIterations(); i++) {
                sampleEdge();

                if (edgeExists()) {
                    if (isDisconnecting()) {
                        removeEdge();
                        reverseDirection();

                        if (totalEdges >= getMaxEdges() || !maxDegreeNotExceeded() || !maxIndegreeNotExceeded() || !maxOutdegreeNotExceeded() || !isAcyclic()) {
                            reverseDirection();
                        }
                        addEdge();
                    } else {
                        removeEdge();
                        totalEdges--;
                    }
                } else {
                    if (totalEdges < getMaxEdges() && maxDegreeNotExceeded() && maxIndegreeNotExceeded() && maxOutdegreeNotExceeded() && isAcyclic()) {
                        addEdge();
                        totalEdges++;
                    }
                }
            }
        }

        private void reverseDirection() {
            int temp = this.randomChild;
            this.randomChild = this.randomParent;
            this.randomParent = temp;
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
         * @return true if the degree of the getModel nodes randomParent and randomChild do not exceed maxDegree.
         */
        private boolean maxDegreeNotExceeded() {
            int parentDegree = this.parentMatrix[this.randomParent][0] + this.childMatrix[this.randomParent][0] - 1;
            int childDegree = this.parentMatrix[this.randomChild][0] + this.childMatrix[this.randomChild][0] - 1;
            return parentDegree <= getMaxDegree() && childDegree <= getMaxDegree();
        }

        /**
         * @return true if the degrees of the getModel nodes randomParent and randomChild do not exceed maxIndegree.
         */
        private boolean maxIndegreeNotExceeded() {
            return this.parentMatrix[this.randomChild][0] <= getMaxInDegree();
        }

        /**
         * @return true if the degrees of the getModel nodes randomParent and randomChild do not exceed maxOutdegree.
         */
        private boolean maxOutdegreeNotExceeded() {
            return this.childMatrix[this.randomParent][0] <= getMaxOutDegree();
        }

        /**
         * @return true iff the random edge randomParent-->randomChild would be disconnecting were it to be removed.
         */
        private boolean isDisconnecting() {
            boolean[] visited = new boolean[getNumNodes()];
            int[] list = new int[getNumNodes()];
            int index = 0;
            int lastIndex = 1;
            list[0] = 0;
            visited[0] = true;
            while (index < lastIndex) {
                int currentNode = list[index];

                // verify parents of getModel node
                for (int i = 1; i < this.parentMatrix[currentNode][0]; i++) {
                    if (currentNode == this.randomChild && this.parentMatrix[currentNode][i] == this.randomParent) {
                        continue;
                    }

                    if (!visited[this.parentMatrix[currentNode][i]]) {
                        list[lastIndex] = this.parentMatrix[currentNode][i];
                        visited[this.parentMatrix[currentNode][i]] = true;
                        lastIndex++;
                    }
                }

                // verify children of getModel node
                for (int i = 1; i < this.childMatrix[currentNode][0]; i++) {
                    if (currentNode == this.randomParent && this.childMatrix[currentNode][i] == this.randomChild) {
                        continue;
                    }

                    if (!visited[this.childMatrix[currentNode][i]]) {
                        list[lastIndex] = this.childMatrix[currentNode][i];
                        visited[this.childMatrix[currentNode][i]] = true;
                        lastIndex++;
                    }
                }

                index++;
            }

            // verify whether all nodes were visited
            for (boolean aVisited : visited) {
                if (!aVisited) {
                    return true;
                }
            }

            return false;
        }


        /**
         * @return true if the graph is still acyclic after the last edge was added. This method only works before
         * adding the random edge, not after removing an edge.
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
                    }
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
            int max = FastMath.max(getMaxInDegree() + getMaxOutDegree(), getMaxDegree());
            max += 1;

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
         * Initializes the graph as a simple ordered tree, 0-->1-->2-->...-->n.
         */
        private void initializeGraphAsChain() {
            this.parentMatrix = new int[getNumNodes()][getMaxDegree() + 2];
            this.childMatrix = new int[getNumNodes()][getMaxDegree() + 2];

            for (int i = 0; i < getNumNodes(); i++) {
                for (int j = 1; j < getMaxDegree() + 1; j++) {
                    this.parentMatrix[i][j] = -5; //set first node
                    this.childMatrix[i][j] = -5;
                }
            }
            this.parentMatrix[0][0] = 1; //set first node
            this.childMatrix[0][0] = 2;    //set first node
            this.childMatrix[0][1] = 1;    //set first node
            this.parentMatrix[getNumNodes() - 1][0] = 2;  //set last node
            this.parentMatrix[getNumNodes() - 1][1] = getNumNodes() - 2;  //set last node
            this.childMatrix[getNumNodes() - 1][0] = 1;     //set last node
            for (int i = 1; i < (getNumNodes() - 1); i++) {  // set the other nodes
                this.parentMatrix[i][0] = 2;
                this.parentMatrix[i][1] = i - 1;
                this.childMatrix[i][0] = 2;
                this.childMatrix[i][1] = i + 1;
            }
        }

        /**
         * Sets randomParent-->randomChild to a random edge, chosen uniformly.
         */
        private void sampleEdge() {
            int rand = this.randomUtil.nextInt(getNumNodes() * (getNumNodes() - 1));
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
            if ((this.parentMatrix[this.randomChild][0] != 1) && (this.childMatrix[this.randomParent][0] != 1)) {
                lastNode = this.parentMatrix[this.randomChild][this.parentMatrix[this.randomChild][0] - 1];
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
                if ((this.childMatrix[this.randomParent][0] != 1) && (this.childMatrix[this.randomParent][0] != 1)) {
                    lastNode = this.childMatrix[this.randomParent][this.childMatrix[this.randomParent][0] - 1];
                    go = true;
                    for (int i = (this.childMatrix[this.randomParent][0] - 1); (i > 0 && go); i--) { // remove element from childMatrix
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

}
