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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;
import nu.xom.*;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;
import static java.lang.Math.min;
import static java.util.Collections.shuffle;
import java.util.regex.Pattern;

/**
 * Basic graph utilities.
 *
 * @author Joseph Ramsey
 */
public final class GraphUtils {

    /**
     * Arranges the nodes in the graph in a circle.
     *
     * @param radius  The radius of the circle in pixels; a good default is 150.
     * @param centerx The x coordinate for the center of the layout.
     * @param centery The y coordinate for the center of the layout.
     */
    public static void circleLayout(Graph graph, int centerx, int centery, int radius) {
        if (graph == null) {
            return;
        }
        List<Node> nodes = graph.getNodes();
        Collections.sort(nodes);

        double rad = 6.28 / nodes.size();
        double phi = .75 * 6.28;    // start from 12 o'clock.

        for (Node node : nodes) {
            int centerX = centerx + (int) (radius * Math.cos(phi));
            int centerY = centery + (int) (radius * Math.sin(phi));

            node.setCenterX(centerX);
            node.setCenterY(centerY);

            phi += rad;
        }
    }

    public static void kamadaKawaiLayout(Graph graph, boolean randomlyInitialized, double naturalEdgeLength, double springConstant, double stopEnergy) {
        KamadaKawaiLayout layout = new KamadaKawaiLayout(graph);
        layout.setRandomlyInitialized(randomlyInitialized);
        layout.setNaturalEdgeLength(naturalEdgeLength);
        layout.setSpringConstant(springConstant);
        layout.setStopEnergy(stopEnergy);
        layout.doLayout();
    }

    public static void fruchtermanReingoldLayout(Graph graph) {
        FruchtermanReingoldLayout layout = new FruchtermanReingoldLayout(graph);
        layout.doLayout();
    }

    public static Graph randomDag(int numNodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return GraphUtils.randomDag(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Dag randomDag(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return new Dag(GraphUtils.randomGraph(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected));
    }

    public static Graph randomGraph(int numNodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return GraphUtils.randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraph(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {

        // It is still unclear whether we should use the random forward edges method or the
        // random uniform method to create random DAGs, hence this method.
        // jdramsey 12/8/2015
        return GraphUtils.randomGraphRandomForwardEdges(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
//        return randomGraphUniform(nodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphUniform(List<Node> nodes, int numLatentConfounders, int maxNumEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
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
        generator.generate();
        Graph dag = generator.getDag(nodes);

        // Create a list of nodes. Add the nodes in the list to the
        // dag. Arrange the nodes in a circle.
        GraphUtils.fixLatents1(numLatentConfounders, dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        return dag;
    }

    private static List<Node> getCommonCauses(Graph dag) {
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

        return GraphUtils.randomGraph(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected);
    }

    public static Graph randomGraphRandomForwardEdges(List<Node> nodes, int numLatentConfounders, int numEdges, int maxDegree, int maxIndegree, int maxOutdegree, boolean connected) {
        return GraphUtils.randomGraphRandomForwardEdges(nodes, numLatentConfounders, numEdges, maxDegree, maxIndegree, maxOutdegree, connected, true);
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

            shuffle(allEdges);

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

        GraphUtils.fixLatents4(numLatentConfounders, dag);

        if (layoutAsCircle) {
            GraphUtils.circleLayout(dag, 200, 200, 150);
        }

        return dag;
    }

    public static Graph scaleFreeGraph(int numNodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        return GraphUtils.scaleFreeGraph(nodes, numLatentConfounders, alpha, beta, delta_in, delta_out);
    }

    private static Graph scaleFreeGraph(List<Node> _nodes, int numLatentConfounders, double alpha, double beta, double delta_in, double delta_out) {

        if (alpha + beta >= 1) {
            throw new IllegalArgumentException("For the Bollobas et al. algorithm," + "\napha + beta + gamma = 1, so alpha + beta must be < 1.");
        }

        shuffle(_nodes);

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
                w = GraphUtils.chooseNode(GraphUtils.indegrees(nodes, parents), delta_in);
                Node m = _nodes.get(v);
                nodes.addFirst(m);
                parents.put(m, new HashSet<>());
                children.put(m, new HashSet<>());
                v = 0;
                w++;
            } else if (r < alpha + beta) {
                v = GraphUtils.chooseNode(GraphUtils.outdegrees(nodes, children), delta_out);
                w = GraphUtils.chooseNode(GraphUtils.indegrees(nodes, parents), delta_in);
                if (!(w > v)) {
                    continue;
                }
            } else {
                v = GraphUtils.chooseNode(GraphUtils.outdegrees(nodes, children), delta_out);
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

        GraphUtils.fixLatents1(numLatentConfounders, G);

        GraphUtils.circleLayout(G, 200, 200, 150);

        return G;
    }

    private static int chooseNode(int[] distribution, double delta) {
        double cumsum = 0.0;
        double psum = GraphUtils.sum(distribution) + delta * distribution.length;
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
        List<Node> commonCauses = GraphUtils.getCommonCauses(graph);
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

        List<Node> commonCausesAndEffects = GraphUtils.getCommonCausesAndEffects(graph);
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
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5
     * to the graph, then finally adding two cycles.
     */
    public static Graph cyclicGraph2(int numNodes, int numEdges, int maxDegree) {

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

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    /**
     * Makes a cyclic graph by repeatedly adding cycles of length of 3, 4, or 5
     * to the graph, then finally adding two cycles.
     */
    public static Graph cyclicGraph3(int numNodes, int numEdges, int maxDegree, double probCycle, double probTwoCycle) {

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

        GraphUtils.circleLayout(graph, 200, 200, 150);

        return graph;
    }

    public static void addTwoCycles(Graph graph, int numTwoCycles) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        shuffle(edges);

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
     * Arranges the nodes in the result graph according to their positions in
     * the source graph.
     *
     * @return true if all the nodes were arranged, false if not.
     */
    public static boolean arrangeBySourceGraph(Graph resultGraph, Graph sourceGraph) {
        if (resultGraph == null) {
            throw new IllegalArgumentException("Graph must not be null.");
        }

        if (sourceGraph == null) {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
            return true;
        }

        boolean arrangedAll = true;

        // There is a source graph. Position the nodes in the
        // result graph correspondingly.
        for (Node o : resultGraph.getNodes()) {
            String name = o.getName();
            Node sourceNode = sourceGraph.getNode(name);

            if (sourceNode == null) {
                arrangedAll = false;
                continue;
            }

            o.setCenterX(sourceNode.getCenterX());
            o.setCenterY(sourceNode.getCenterY());
        }

        return arrangedAll;
    }

    public static void arrangeByLayout(Graph graph, HashMap<String, PointXy> layout) {
        for (Node node : graph.getNodes()) {
            PointXy point = layout.get(node.getName());
            node.setCenter(point.getX(), point.getY());
        }
    }

    /**
     * @return the node associated with a given error node. This should be the
     * only child of the error node, E --&gt; N.
     */
    public static Node getAssociatedNode(Node errorNode, Graph graph) {
        if (errorNode.getNodeType() != NodeType.ERROR) {
            throw new IllegalArgumentException("Can only get an associated node " + "for an error node: " + errorNode);
        }

        List<Node> children = graph.getChildren(errorNode);

        if (children.size() != 1) {
            System.out.println("children of " + errorNode + " = " + children);
            System.out.println(graph);

            throw new IllegalArgumentException("An error node should have only " + "one child, which is its associated node: " + errorNode);
        }

        return children.get(0);
    }

    /**
     * @return true if <code>set</code> is a clique in <code>graph</code>.
     * R. Silva, June 2004
     */
    public static boolean isClique(Collection<Node> set, Graph graph) {
        List<Node> setv = new LinkedList<>(set);
        for (int i = 0; i < setv.size() - 1; i++) {
            for (int j = i + 1; j < setv.size(); j++) {
                if (!graph.isAdjacentTo(setv.get(i), setv.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Graph loadRSpecial(File file) {
        DataSet eg = null;

        try {
            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(file.toPath(), Delimiter.COMMA);
            reader.setHasHeader(false);
            Data data = reader.readInData();
            eg = (DataSet) DataConvertUtils.toDataModel(data);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        if (eg == null) throw new NullPointerException();

        List<Node> vars = eg.getVariables();

        Graph graph = new EdgeListGraph(vars);

        for (int i = 0; i < vars.size(); i++) {
            for (int j = 0; j < vars.size(); j++) {
                if (i == j) continue;
                if (eg.getDouble(i, j) == 1 && eg.getDouble(j, i) == 1) {
                    if (!graph.isAdjacentTo(vars.get(i), vars.get(j))) {
                        graph.addUndirectedEdge(vars.get(i), vars.get(j));
                    }
                } else if (eg.getDouble(i, j) == 1 && eg.getDouble(j, i) == 0) {
                    graph.addDirectedEdge(vars.get(i), vars.get(j));
                }
            }
        }

        return graph;
    }

    /**
     * Calculates the Markov blanket of a target in a DAG. This includes the
     * target, the parents of the target, the children of the target, the
     * parents of the children of the target, edges from parents to target,
     * target to children, parents of children to children, and parent to
     * parents of children. (Edges among children are implied by the inclusion
     * of edges from parents of children to children.) Edges among parents and
     * among parents of children not explicitly included above are not included.
     * (Joseph Ramsey 8/6/04)
     *
     * @param target a node in the given DAG.
     * @param dag    the DAG with respect to which a Markov blanket DAG is to be
     *               calculated. All the nodes and edges of the Markov Blanket DAG are in
     *               this DAG.
     */
    public static Graph markovBlanketDag(Node target, Graph dag) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.NAME);

        if (dag.getNode(target.getName()) == null) {
            throw new NullPointerException("Target node not in graph: " + target);
        }

        Graph blanket = new EdgeListGraph();
        blanket.addNode(target);

        // Add parents of target.
        List<Node> parents = dag.getParents(target);
        for (Node parent1 : parents) {
            blanket.addNode(parent1);
            blanket.addDirectedEdge(parent1, target);
        }

        // Add children of target and parents of children of target.
        List<Node> children = dag.getChildren(target);
        List<Node> parentsOfChildren = new LinkedList<>();
        for (Node child : children) {
            if (!blanket.containsNode(child)) {
                blanket.addNode(child);
            }

            blanket.addDirectedEdge(target, child);

            List<Node> parentsOfChild = dag.getParents(child);
            parentsOfChild.remove(target);
            for (Node aParentsOfChild : parentsOfChild) {
                if (!parentsOfChildren.contains(aParentsOfChild)) {
                    parentsOfChildren.add(aParentsOfChild);
                }

                if (!blanket.containsNode(aParentsOfChild)) {
                    blanket.addNode(aParentsOfChild);
                }

                blanket.addDirectedEdge(aParentsOfChild, child);
            }
        }

        // Add in edges connecting parents and parents of children.
        parentsOfChildren.removeAll(parents);

        for (Node parent2 : parents) {
            for (Node aParentsOfChildren : parentsOfChildren) {
                Edge edge1 = dag.getEdge(parent2, aParentsOfChildren);
                Edge edge2 = blanket.getEdge(parent2, aParentsOfChildren);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(parent2, aParentsOfChildren, edge1.getProximalEndpoint(parent2), edge1.getProximalEndpoint(aParentsOfChildren));

                    blanket.addEdge(newEdge);
                }
            }
        }

        // Add in edges connecting children and parents of children.
        for (Node aChildren1 : children) {

            for (Node aParentsOfChildren : parentsOfChildren) {
                Edge edge1 = dag.getEdge(aChildren1, aParentsOfChildren);
                Edge edge2 = blanket.getEdge(aChildren1, aParentsOfChildren);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(aChildren1, aParentsOfChildren, edge1.getProximalEndpoint(aChildren1), edge1.getProximalEndpoint(aParentsOfChildren));

                    blanket.addEdge(newEdge);
                }
            }
        }

        return blanket;
    }

    //all adjancencies are directed <=> there is no uncertainty about whom the parents of 'node' are.
    public static boolean allAdjacenciesAreDirected(Node node, Graph graph) {
        List<Edge> nodeEdges = graph.getEdges(node);
        for (Edge edge : nodeEdges) {
            if (!edge.isDirected()) {
                return false;
            }
        }
        return true;
    }

    public static Graph removeBidirectedOrientations(Graph estCpdag) {
        estCpdag = new EdgeListGraph(estCpdag);

        // Make bidirected edges undirected.
        for (Edge edge : estCpdag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                estCpdag.removeEdge(edge);
                estCpdag.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return estCpdag;
    }

    public static Graph removeBidirectedEdges(Graph estCpdag) {
        estCpdag = new EdgeListGraph(estCpdag);

        // Remove bidirected edges altogether.
        for (Edge edge : new ArrayList<>(estCpdag.getEdges())) {
            if (Edges.isBidirectedEdge(edge)) {
                estCpdag.removeEdge(edge);
            }
        }

        return estCpdag;
    }

    public static Graph undirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return graph2;
    }

    public static Graph completeGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        graph2.removeEdges(new ArrayList<>(graph2.getEdges()));

        List<Node> nodes = graph2.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);
                graph2.addUndirectedEdge(node1, node2);
            }
        }

        return graph2;
    }

    /**
     * @return the edges that are in <code>graph1</code> but not in
     * <code>graph2</code>, as a list of undirected edges..
     */
    public static List<Edge> adjacenciesComplement(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<>();

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            if (node21 == null || node22 == null || !graph2.isAdjacentTo(node21, node22)) {
                edges.add(Edges.nondirectedEdge(edge1.getNode1(), edge1.getNode2()));
            }
        }

        return edges;
    }

    /**
     * @return a new graph in which the bidirectred edges of the given graph
     * have been changed to undirected edges.
     */
    public static Graph bidirectedToUndirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    /**
     * @return a new graph in which the undirectred edges of the given graph
     * have been changed to bidirected edges.
     */
    public static Graph undirectedToBidirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addBidirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    public static String pathString(Graph graph, List<Node> path) {
        return GraphUtils.pathString(graph, path, new LinkedList<>());
    }

    public static String pathString(Graph graph, Node... x) {
        List<Node> path = new ArrayList<>();
        Collections.addAll(path, x);
        return GraphUtils.pathString(graph, path, new LinkedList<>());
    }

    private static String pathString(Graph graph, List<Node> path, List<Node> conditioningVars) {
        StringBuilder buf = new StringBuilder();

        if (path.size() < 2) {
            return "NO PATH";
        }

        if (path.get(0).getNodeType() == NodeType.LATENT) {
            buf.append("(").append(path.get(0).toString()).append(")");
        } else {
            buf.append(path.get(0).toString());
        }


        if (conditioningVars.contains(path.get(0))) {
            buf.append("(C)");
        }

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);

            Edge edge = graph.getEdge(n0, n1);

            if (edge == null) {
                buf.append("(-)");
            } else {
                Endpoint endpoint0 = edge.getProximalEndpoint(n0);
                Endpoint endpoint1 = edge.getProximalEndpoint(n1);

                if (endpoint0 == Endpoint.ARROW) {
                    buf.append("<");
                } else if (endpoint0 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint0 == Endpoint.CIRCLE) {
                    buf.append("o");
                }

                buf.append("-");

                if (endpoint1 == Endpoint.ARROW) {
                    buf.append(">");
                } else if (endpoint1 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint1 == Endpoint.CIRCLE) {
                    buf.append("o");
                }
            }

            if (n1.getNodeType() == NodeType.LATENT) {
                buf.append("(").append(n1).append(")");
            } else {
                buf.append(n1);
            }

            if (conditioningVars.contains(n1)) {
                buf.append("(C)");
            }
        }
        return buf.toString();
    }

    /**
     * Converts the given graph, <code>originalGraph</code>, to use the new
     * variables (with the same names as the old).
     *
     * @param originalGraph The graph to be converted.
     * @param newVariables  The new variables to use, with the same names as the
     *                      old ones.
     * @return A new, converted, graph.
     */
    public static Graph replaceNodes(Graph originalGraph, List<Node> newVariables) {
        Map<String, Node> newNodes = new HashMap<>();
        List<Node> _newNodes = new ArrayList<>();

        for (Node node : newVariables) {
            if (node.getNodeType() != NodeType.LATENT) {
                newNodes.put(node.getName(), node);
                _newNodes.add(node);
            }
        }

        Graph convertedGraph = new EdgeListGraph(_newNodes);

        for (Edge edge : originalGraph.getEdges()) {
            Node node1 = newNodes.get(edge.getNode1().getName());
            Node node2 = newNodes.get(edge.getNode2().getName());

            if (node1 == null) {
                node1 = edge.getNode1();
            }

            if (!convertedGraph.containsNode(node1)) {
                convertedGraph.addNode(node1);
            }

            if (node2 == null) {
                node2 = edge.getNode2();
            }

            if (!convertedGraph.containsNode(node2)) {
                convertedGraph.addNode(node2);
            }

            if (!convertedGraph.containsNode(node1)) {
                convertedGraph.addNode(node1);
            }

            if (!convertedGraph.containsNode(node2)) {
                convertedGraph.addNode(node2);
            }

            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();
            Edge newEdge = new Edge(node1, node2, endpoint1, endpoint2);
            convertedGraph.addEdge(newEdge);
        }

        for (Triple triple : originalGraph.underlines().getUnderLines()) {
            convertedGraph.underlines().addUnderlineTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        for (Triple triple : originalGraph.underlines().getDottedUnderlines()) {
            convertedGraph.underlines().addDottedUnderlineTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        for (Triple triple : originalGraph.underlines().getAmbiguousTriples()) {
            convertedGraph.underlines().addAmbiguousTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        return convertedGraph;
    }

    public static Graph restrictToMeasured(Graph graph) {
        graph = new EdgeListGraph(graph);

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                graph.removeNode(node);
            }
        }

        return graph;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the
     * new variables (with the same names as the old).
     *
     * @param originalNodes The list of nodes to be converted.
     * @param newNodes      A list of new nodes, containing as a subset nodes with
     *                      the same names as those in <code>originalNodes</code>. the old ones.
     * @return The converted list of nodes.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, List<Node> newNodes) {
        List<Node> convertedNodes = new LinkedList<>();

        for (Node node : originalNodes) {
            if (node == null) {
                throw new NullPointerException("Null node among original nodes.");
            }

            for (Node _node : newNodes) {
                if (_node == null) {
                    throw new NullPointerException("Null node among new nodes.");
                }

                if (node.getName().equals(_node.getName())) {
                    convertedNodes.add(_node);
                    break;
                }
            }
        }

        return convertedNodes;
    }

    /**
     * Counts the adjacencies that are in graph1 but not in graph2.
     *
     * @throws IllegalArgumentException if graph1 and graph2 are not namewise
     *                                  isomorphic.
     */
    public static int countAdjErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int count = 0;

        Set<Edge> edges1 = graph1.getEdges();

        for (Edge edge : edges1) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                ++count;
            }
        }

        return count;
    }

    /**
     * Counts the arrowpoints that are in graph1 but not in graph2.
     */
    public static int countArrowptErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int count = 0;

        for (Edge edge1 : graph1.getEdges()) {
            Node node1 = edge1.getNode1();
            Node node2 = edge1.getNode2();

            Edge edge2 = graph2.getEdge(node1, node2);

            if (edge1.getEndpoint1() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode1()) != Endpoint.ARROW) {
                    count++;
                }
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode2()) != Endpoint.ARROW) {
                    count++;
                }
            }
        }

        for (Edge edge1 : graph2.getEdges()) {
            Node node1 = edge1.getNode1();
            Node node2 = edge1.getNode2();

            Edge edge2 = graph1.getEdge(node1, node2);

            if (edge1.getEndpoint1() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode1()) != Endpoint.ARROW) {
                    count++;
                }
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode2()) != Endpoint.ARROW) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int getNumCorrectArrowpts(Graph correct, Graph estimated) {
        correct = GraphUtils.replaceNodes(correct, estimated.getNodes());

        Set<Edge> edges = estimated.getEdges();
        int numCorrect = 0;

        for (Edge estEdge : edges) {
            Edge correctEdge = correct.getEdge(estEdge.getNode1(), estEdge.getNode2());
            if (correctEdge == null) {
                continue;
            }

            if (estEdge.getProximalEndpoint(estEdge.getNode1()) == Endpoint.ARROW && correctEdge.getProximalEndpoint(estEdge.getNode1()) == Endpoint.ARROW) {
                numCorrect++;
            }

            if (estEdge.getProximalEndpoint(estEdge.getNode2()) == Endpoint.ARROW && correctEdge.getProximalEndpoint(estEdge.getNode2()) == Endpoint.ARROW) {
                numCorrect++;
            }
        }

        return numCorrect;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the
     * replacement nodes for them by the same name in the given
     * <code>graph</code>.
     *
     * @param originalNodes The list of nodes to be converted.
     * @param graph         A graph to be used as a source of new nodes.
     * @return A new, converted, graph.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, Graph graph) {
        List<Node> convertedNodes = new LinkedList<>();

        for (Node node : originalNodes) {
            convertedNodes.add(graph.getNode(node.getName()));
        }

        return convertedNodes;
    }

    /**
     * @return an empty graph with the given number of nodes.
     */
    public static Graph emptyGraph(int numNodes) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + i));
        }

        return new EdgeListGraph(nodes);
    }

    /**
     * Converts a graph to a Graphviz .dot file
     */
    public static String graphToDot(Graph graph) {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph g {\n");
        for (Edge edge : graph.getEdges()) {
            String n1 = edge.getNode1().getName();
            String n2 = edge.getNode2().getName();

            Endpoint end1 = edge.getEndpoint1();
            Endpoint end2 = edge.getEndpoint2();

            if (n1.compareTo(n2) > 0) {
                String temp = n1;
                n1 = n2;
                n2 = temp;

                Endpoint tmp = end1;
                end1 = end2;
                end2 = tmp;
            }
            builder.append(" \"").append(n1).append("\" -> \"").append(n2).append("\" [");

            if (end1 != Endpoint.TAIL) {
                builder.append("dir=both, ");
            }

            builder.append("arrowtail=");
            if (end1 == Endpoint.ARROW) {
                builder.append("normal");
            } else if (end1 == Endpoint.TAIL) {
                builder.append("none");
            } else if (end1 == Endpoint.CIRCLE) {
                builder.append("odot");
            }
            builder.append(", arrowhead=");
            if (end2 == Endpoint.ARROW) {
                builder.append("normal");
            } else if (end2 == Endpoint.TAIL) {
                builder.append("none");
            } else if (end2 == Endpoint.CIRCLE) {
                builder.append("odot");
            }

            // Bootstrapping
            List<EdgeTypeProbability> edgeTypeProbabilities = edge.getEdgeTypeProbabilities();
            if (edgeTypeProbabilities != null && !edgeTypeProbabilities.isEmpty()) {
                StringBuilder label = new StringBuilder(n1 + " - " + n2);
                for (EdgeTypeProbability edgeTypeProbability : edgeTypeProbabilities) {
                    EdgeType edgeType = edgeTypeProbability.getEdgeType();
                    double probability = edgeTypeProbability.getProbability();
                    if (probability > 0) {
                        StringBuilder edgeTypeString = new StringBuilder();
                        switch (edgeType) {
                            case nil:
                                edgeTypeString = new StringBuilder("no edge");
                                break;
                            case ta:
                                edgeTypeString = new StringBuilder("-->");
                                break;
                            case at:
                                edgeTypeString = new StringBuilder("<--");
                                break;
                            case ca:
                                edgeTypeString = new StringBuilder("o->");
                                break;
                            case ac:
                                edgeTypeString = new StringBuilder("<-o");
                                break;
                            case cc:
                                edgeTypeString = new StringBuilder("o-o");
                                break;
                            case aa:
                                edgeTypeString = new StringBuilder("<->");
                                break;
                            case tt:
                                edgeTypeString = new StringBuilder("---");
                                break;
                        }

                        List<Property> properties = edgeTypeProbability.getProperties();
                        if (properties != null && properties.size() > 0) {
                            for (Property property : properties) {
                                edgeTypeString.append(" ").append(property.toString());
                            }
                        }

                        label.append("\\n[").append(edgeTypeString).append("]:").append(edgeTypeProbability.getProbability());
                    }
                }
                builder.append(", label=\"").append(label).append("\", fontname=courier");
            }

            builder.append("]; \n");
        }
        builder.append("}");

        return builder.toString();
    }

    public static void graphToDot(Graph graph, File file) {
        try {
            Writer writer = new FileWriter(file);
            writer.write(graphToDot(graph));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return an XML element representing the given graph. (Well, only a basic
     * graph for now...)
     */
    public static Element convertToXml(Graph graph) {
        Element element = new Element("graph");

        Element variables = new Element("variables");
        element.appendChild(variables);

        for (Node node : graph.getNodes()) {
            Element variable = new Element("variable");
            Text text = new Text(node.getName());
            variable.appendChild(text);
            variables.appendChild(variable);
        }

        Element edges = new Element("edges");
        element.appendChild(edges);

        for (Edge edge : graph.getEdges()) {
            Element _edge = new Element("edge");
            Text text = new Text(edge.toString());
            _edge.appendChild(text);
            edges.appendChild(_edge);
        }

        Set<Triple> ambiguousTriples = graph.underlines().getAmbiguousTriples();

        if (!ambiguousTriples.isEmpty()) {
            Element underlinings = new Element("ambiguities");
            element.appendChild(underlinings);

            for (Triple triple : ambiguousTriples) {
                Element underlining = new Element("ambiguities");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> underlineTriples = graph.underlines().getUnderLines();

        if (!underlineTriples.isEmpty()) {
            Element underlinings = new Element("underlines");
            element.appendChild(underlinings);

            for (Triple triple : underlineTriples) {
                Element underlining = new Element("underline");
                Text text = new Text(niceTripleString(triple));
                underlining.appendChild(text);
                underlinings.appendChild(underlining);
            }
        }

        Set<Triple> dottedTriples = graph.underlines().getDottedUnderlines();

        if (!dottedTriples.isEmpty()) {
            Element dottedUnderlinings = new Element("dottedUnderlines");
            element.appendChild(dottedUnderlinings);

            for (Triple triple : dottedTriples) {
                Element dottedUnderlining = new Element("dottedUnderline");
                Text text = new Text(niceTripleString(triple));
                dottedUnderlining.appendChild(text);
                dottedUnderlinings.appendChild(dottedUnderlining);
            }
        }

        return element;
    }

    private static String niceTripleString(Triple triple) {
        return triple.getX() + ", " + triple.getY() + ", " + triple.getZ();
    }

    public static String graphToXml(Graph graph) {
        Document document = new Document(convertToXml(graph));
        OutputStream out = new ByteArrayOutputStream();
        Serializer serializer = new Serializer(out);
        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return out.toString();
    }

    public static String graphToPcalg(Graph g) {
        Map<Endpoint, Integer> mark2Int = new HashMap();
        mark2Int.put(Endpoint.NULL, 0);
        mark2Int.put(Endpoint.CIRCLE, 1);
        mark2Int.put(Endpoint.ARROW, 2);
        mark2Int.put(Endpoint.TAIL, 3);

        int n = g.getNumNodes();
        int[][] A = new int[n][n];

        List<Node> nodes = g.getNodes();
        for (Edge edge : g.getEdges()) {
            int i = nodes.indexOf(edge.getNode1());
            int j = nodes.indexOf(edge.getNode2());
            A[j][i] = mark2Int.get(edge.getEndpoint1());
            A[i][j] = mark2Int.get(edge.getEndpoint2());
        }

        TextTable table = new TextTable(n + 1, n);
        table.setDelimiter(TextTable.Delimiter.COMMA);

        for (int j = 0; j < n; j++) {
            table.setToken(0, j, nodes.get(j).getName());
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                table.setToken(i + 1, j, "" + A[i][j]);
            }
        }

        return table.toString();
    }

    public static Graph parseGraphXml(Element graphElement, Map<String, Node> nodes) throws ParsingException {
        if (!"graph".equals(graphElement.getLocalName())) {
            throw new IllegalArgumentException("Expecting graph element: " + graphElement.getLocalName());
        }

        if (!("variables".equals(graphElement.getChildElements().get(0).getLocalName()))) {
            throw new ParsingException("Expecting variables element: " + graphElement.getChildElements().get(0).getLocalName());
        }

        Element variablesElement = graphElement.getChildElements().get(0);
        Elements variableElements = variablesElement.getChildElements();
        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < variableElements.size(); i++) {
            Element variableElement = variableElements.get(i);

            if (!("variable".equals(variablesElement.getChildElements().get(i).getLocalName()))) {
                throw new ParsingException("Expecting variable element.");
            }

            String value = variableElement.getValue();

            if (nodes == null) {
                variables.add(new GraphNode(value));
            } else {
                variables.add(nodes.get(value));
            }
        }

        Graph graph = new EdgeListGraph(variables);

//        graphNotes.add(noteAttribute.getValue());
        if (!("edges".equals(graphElement.getChildElements().get(1).getLocalName()))) {
            throw new ParsingException("Expecting edges element.");
        }

        Element edgesElement = graphElement.getChildElements().get(1);
        Elements edgesElements = edgesElement.getChildElements();

        for (int i = 0; i < edgesElements.size(); i++) {
            Element edgeElement = edgesElements.get(i);

            if (!("edge".equals(edgeElement.getLocalName()))) {
                throw new ParsingException("Expecting edge element: " + edgeElement.getLocalName());
            }

            String value = edgeElement.getValue();

            final String regex = "([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*) ?(.)-(.) ?([A-Za-z0-9_-]*:?[A-Za-z0-9_-]*)";
//            String regex = "([A-Za-z0-9_-]*) ?([<o])-([o>]) ?([A-Za-z0-9_-]*)";

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value);

            if (!matcher.matches()) {
                throw new ParsingException("Edge doesn't match pattern.");
            }

            String var1 = matcher.group(1);
            String leftEndpoint = matcher.group(2);
            String rightEndpoint = matcher.group(3);
            String var2 = matcher.group(4);

            Node node1 = graph.getNode(var1);
            Node node2 = graph.getNode(var2);
            Endpoint endpoint1;

            switch (leftEndpoint) {
                case "<":
                    endpoint1 = Endpoint.ARROW;
                    break;
                case "o":
                    endpoint1 = Endpoint.CIRCLE;
                    break;
                case "-":
                    endpoint1 = Endpoint.TAIL;
                    break;
                default:
                    throw new IllegalStateException("Expecting an endpoint: " + leftEndpoint);
            }

            Endpoint endpoint2;

            switch (rightEndpoint) {
                case ">":
                    endpoint2 = Endpoint.ARROW;
                    break;
                case "o":
                    endpoint2 = Endpoint.CIRCLE;
                    break;
                case "-":
                    endpoint2 = Endpoint.TAIL;
                    break;
                default:
                    throw new IllegalStateException("Expecting an endpoint: " + rightEndpoint);
            }

            Edge edge = new Edge(node1, node2, endpoint1, endpoint2);
            graph.addEdge(edge);
        }

        int size = graphElement.getChildElements().size();
        if (2 >= size) {
            return graph;
        }

        int p = 2;

        if ("ambiguities".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "ambiguity");
            graph.underlines().setAmbiguousTriples(triples);
            p++;
        }

        if (p >= size) {
            return graph;
        }

        if ("underlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "underline");
            graph.underlines().setUnderLineTriples(triples);
            p++;
        }

        if (p >= size) {
            return graph;
        }

        if ("dottedunderlines".equals(graphElement.getChildElements().get(p).getLocalName())) {
            Element ambiguitiesElement = graphElement.getChildElements().get(p);
            Set<Triple> triples = parseTriples(variables, ambiguitiesElement, "dottedunderline");
            graph.underlines().setDottedUnderLineTriples(triples);
        }

        return graph;
    }

    /**
     * A triples element has a list of three (comman separated) nodes as text.
     */
    private static Set<Triple> parseTriples(List<Node> variables, Element triplesElement, String s) {
        Elements elements = triplesElement.getChildElements(s);

        Set<Triple> triples = new HashSet<>();

        for (int q = 0; q < elements.size(); q++) {
            Element tripleElement = elements.get(q);
            String value = tripleElement.getValue();

            String[] tokens = value.split(",");

            if (tokens.length != 3) {
                throw new IllegalArgumentException("Expecting a triple: " + value);
            }

            String x = tokens[0].trim();
            String y = tokens[1].trim();
            String z = tokens[2].trim();

            Node _x = getNode(variables, x);
            Node _y = getNode(variables, y);
            Node _z = getNode(variables, z);

            Triple triple = new Triple(_x, _y, _z);
            triples.add(triple);
        }
        return triples;
    }

    private static Node getNode(List<Node> nodes, String x) {
        for (Node node : nodes) {
            if (x.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public static Element getRootElement(File file) throws ParsingException, IOException {
        Builder builder = new Builder();
        Document document = builder.build(file);
        return document.getRootElement();
    }

    /**
     * @param graph The graph to be saved.
     * @param file  The file to save it in.
     * @param xml   True if to be saved in XML, false if in text.
     * @return I have no idea whey I'm returning this; it's already closed...
     */
    public static PrintWriter saveGraph(Graph graph, File file, boolean xml) {
        PrintWriter out;

        try {
            out = new PrintWriter(new FileOutputStream(file));
//            out.print(graph);

            if (xml) {
//                out.println(graphToPcalg(graph));
                out.print(graphToXml(graph));
            } else {
                out.print(graph);
            }
            out.flush();
            out.close();
        } catch (IOException e1) {
            throw new IllegalArgumentException("Output file could not " + "be opened: " + file);
        }
        return out;
    }

    public static Graph loadGraph(File file) {

        Element root;
        Graph graph;

        try {
            root = getRootElement(file);
            graph = parseGraphXml(root, null);
        } catch (ParsingException e1) {
            throw new IllegalArgumentException("Could not parse " + file, e1);
        } catch (IOException e1) {
            throw new IllegalArgumentException("Could not read " + file, e1);
        }

        return graph;
    }

    public static Graph loadGraphTxt(File file) {
        try {
            Reader in1 = new FileReader(file);
            return readerToGraphTxt(in1);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    public static Graph loadGraphRuben(File file) {
        try {
            final String commentMarker = "//";
            final char quoteCharacter = '"';
            final String missingValueMarker = "*";
            final boolean hasHeader = false;

            DataSet dataSet = DataUtils.loadContinuousData(file, commentMarker, quoteCharacter, missingValueMarker, hasHeader, Delimiter.TAB);

            List<Node> nodes = dataSet.getVariables();
            Graph graph = new EdgeListGraph(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (dataSet.getDouble(i, j) != 0D) {
                        graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                    }
                }
            }

            return graph;

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    public static Graph loadGraphJson(File file) {
        try {
            Reader in1 = new FileReader(file);
            return readerToGraphJson(in1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalStateException();
    }

    public static Graph readerToGraphTxt(String graphString) throws IOException {
        return readerToGraphTxt(new CharArrayReader(graphString.toCharArray()));
    }

    public static Graph readerToGraphTxt(Reader reader) throws IOException {
        Graph graph = new EdgeListGraph();
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();
                switch (line) {
                    case "Graph Nodes:":
                        extractGraphNodes(graph, in);
                        break;
                    case "Graph Edges:":
                        extractGraphEdges(graph, in);
                        break;
                }
            }
        }

        return graph;
    }

    public static Graph readerToGraphRuben(Reader reader) throws IOException {
        Graph graph = new EdgeListGraph();
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();
                switch (line) {
                    case "Graph Nodes:":
                        extractGraphNodes(graph, in);
                        break;
                    case "Graph Edges:":
                        extractGraphEdges(graph, in);
                        break;
                }
            }
        }

        return graph;
    }
    
    private static void extractGraphEdges(Graph graph, BufferedReader in) throws IOException {
        Pattern lineNumPattern = Pattern.compile("^\\d+.\\s?");
        Pattern spacePattern = Pattern.compile("\\s+");
        Pattern semicolonPattern = Pattern.compile(";");
        Pattern colonPattern = Pattern.compile(":");
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            line = line.trim();
            if (line.isEmpty()) {
                return;
            }

            line = lineNumPattern.matcher(line).replaceAll("");
            String[] fields = spacePattern.split(line, 4);
            Edge edge = getEdge(fields[0], fields[1], fields[2], graph);
            if (fields.length > 3) {
                fields = semicolonPattern.split(fields[3]);
                if (fields.length > 1) {
                    for (String prop : fields) {
                        setEdgeTypeProperties(prop, edge, graph, spacePattern, colonPattern);
                    }
                } else {
                    getEdgeProperties(fields[0], spacePattern)
                            .forEach(edge::addProperty);
                }
            }

            graph.addEdge(edge);
        }
    }

    private static void setEdgeTypeProperties(String prop, Edge edge, Graph graph, Pattern spacePattern, Pattern colonPattern) {
        prop = prop.replace("[", "").replace("]", "");
        String[] fields = colonPattern.split(prop);
        if (fields.length == 2) {
            String bootstrapEdge = fields[0];
            String bootstrapEdgeTypeProb = fields[1];

            // edge type
            fields = spacePattern.split(bootstrapEdge, 4);
            if (fields.length >= 3) {
                // edge-type probability
                EdgeTypeProbability.EdgeType edgeType = getEdgeType(fields[1]);
                List<Edge.Property> properties = new LinkedList<>();
                if (fields.length > 3) {
                    // pags
                    properties.addAll(getEdgeProperties(fields[3], spacePattern));
                }

                edge.addEdgeTypeProbability(new EdgeTypeProbability(edgeType, properties, Double.parseDouble(bootstrapEdgeTypeProb)));
            } else {
                // edge probability
                if ("edge".equals(bootstrapEdge)) {
                    fields = spacePattern.split(bootstrapEdgeTypeProb, 2);
                    if (fields.length > 1) {
                        edge.setProbability(Double.parseDouble(fields[0]));
                        getEdgeProperties(fields[1], spacePattern).forEach(edge::addProperty);
                    } else {
                        edge.setProbability(Double.parseDouble(bootstrapEdgeTypeProb));
                    }
                } else if ("no edge".equals(bootstrapEdge)) {
                    fields = spacePattern.split(bootstrapEdgeTypeProb);
                    edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.nil, Double.parseDouble(bootstrapEdgeTypeProb)));
                }
            }
        }
    }

    private static EdgeTypeProbability.EdgeType getEdgeType(String edgeType) {
        Endpoint endpointFrom = getEndpoint(edgeType.charAt(0));
        Endpoint endpointTo = getEndpoint(edgeType.charAt(2));

        if (endpointFrom == Endpoint.TAIL && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ta;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.at;
        } else if (endpointFrom == Endpoint.CIRCLE && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ca;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.ac;
        } else if (endpointFrom == Endpoint.CIRCLE && endpointTo == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.cc;
        } else if (endpointFrom == Endpoint.ARROW && endpointTo == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.aa;
        } else if (endpointFrom == Endpoint.TAIL && endpointTo == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.tt;
        } else {
            return EdgeTypeProbability.EdgeType.nil;
        }
    }

    private static List<Edge.Property> getEdgeProperties(String props, Pattern spacePattern) {
        List<Edge.Property> properties = new LinkedList<>();

        for (String prop : spacePattern.split(props)) {
            if ("dd".equals(prop)) {
                properties.add(Edge.Property.dd);
            } else if ("nl".equals(prop)) {
                properties.add(Edge.Property.nl);
            } else if ("pd".equals(prop)) {
                properties.add(Edge.Property.pd);
            } else if ("pl".equals(prop)) {
                properties.add(Edge.Property.pl);
            }
        }

        return properties;
    }

    private static Edge getEdge(String nodeNameFrom, String edgeType, String nodeNameTo, Graph graph) {
        Node nodeFrom = getNode(nodeNameFrom, graph);
        Node nodeTo = getNode(nodeNameTo, graph);
        Endpoint endpointFrom = getEndpoint(edgeType.charAt(0));
        Endpoint endpointTo = getEndpoint(edgeType.charAt(2));

        return new Edge(nodeFrom, nodeTo, endpointFrom, endpointTo);
    }

    private static Endpoint getEndpoint(char endpoint) {
        if (endpoint == '>' || endpoint == '<') {
            return Endpoint.ARROW;
        } else if (endpoint == 'o') {
            return Endpoint.CIRCLE;
        } else if (endpoint == '-') {
            return Endpoint.TAIL;
        } else {
            throw new IllegalArgumentException(String.format("Unrecognized endpoint: %s.", endpoint));
        }
    }

    private static Node getNode(String nodeName, Graph graph) {
        Node node = graph.getNode(nodeName);
        if (node == null) {
            graph.addNode(new GraphNode(nodeName));
            node = graph.getNode(nodeName);
        }

        return node;
    }

    private static void extractGraphNodes(Graph graph, BufferedReader in) throws IOException {
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            String[] tokens = line.split("[,;]");

            for (String token : tokens) {
                if (token.startsWith("(") && token.endsWith(")")) {
                    token = token.replace("(", "");
                    token = token.replace(")", "");
                    Node node = new GraphNode(token);
                    node.setNodeType(NodeType.LATENT);
                    graph.addNode(node);
                } else {
                    Node node = new GraphNode(token);
                    node.setNodeType(NodeType.MEASURED);
                    graph.addNode(node);
                }
            }

//            Arrays.stream(line.split("[,;]")).map(GraphNode::new).forEach(graph::addNode);
        }
    }

    public static Graph readerToGraphJson(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);

        StringBuilder json = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            json.append(line.trim());
        }

        return JsonUtils.parseJSONObjectToTetradGraph(json.toString());
    }

    public static Graph loadGraphGcpCausaldag(File file) {
        System.out.println("KK " + file.getAbsolutePath());
        File parentFile = file.getParentFile().getParentFile();
        parentFile = new File(parentFile, "data");
        File dataFile = new File(parentFile, file.getName().replace("causaldag.gsp", "data"));

        System.out.println(dataFile.getAbsolutePath());

        List<Node> variables = null;

        try {
            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(dataFile.toPath(), Delimiter.TAB);
            Data data = reader.readInData();

            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(data);

            variables = dataSet.getVariables();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Reader in1 = new FileReader(file);
            return GraphUtils.readerToGraphCausaldag(in1, variables);

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    public static Graph readerToGraphCausaldag(Reader reader, List<Node> variables) throws IOException {
        Graph graph = new EdgeListGraph(variables);
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();

                String[] tokens = line.split("[\\[\\]]");

                for (String t : tokens) {
//                    System.out.println(t);

                    String[] tokens2 = t.split("[,|]");

                    if (tokens2[0].isEmpty()) continue;

                    Node x = variables.get(Integer.parseInt(tokens2[0]));

                    for (int j = 1; j < tokens2.length; j++) {
                        if (tokens2[j].isEmpty()) continue;

                        Node y = variables.get(Integer.parseInt(tokens2[j]));

                        graph.addDirectedEdge(y, x);
                    }
                }
            }
        }

        return graph;
    }

    public static HashMap<String, PointXy> grabLayout(List<Node> nodes) {
        HashMap<String, PointXy> layout = new HashMap<>();

        for (Node node : nodes) {
            layout.put(node.getName(), new PointXy(node.getCenterX(), node.getCenterY()));
        }

        return layout;
    }

    /**
     * @return A list of triples of the form X*-&gt;Y&lt;-*Z.
     */
    public static List<Triple> getCollidersFromGraph(Node node, Graph graph) {
        List<Triple> colliders = new ArrayList<>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.ARROW) {
                colliders.add(new Triple(x, node, z));
            }
        }

        return colliders;
    }

    /**
     * @return A list of triples of the form X, Y, Z, where X, Y, Z is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getNoncollidersFromGraph(Node node, Graph graph) {
        List<Triple> noncolliders = new ArrayList<>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.TAIL || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.ARROW || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.TAIL) {
                noncolliders.add(new Triple(x, node, z));
            }
        }

        return noncolliders;
    }

    /**
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getAmbiguousTriplesFromGraph(Node node, Graph graph) {
        List<Triple> ambiguousTriples = new ArrayList<>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.underlines().isAmbiguousTriple(x, node, z)) {
                ambiguousTriples.add(new Triple(x, node, z));
            }
        }

        return ambiguousTriples;
    }

    /**
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> underlinedTriples = new ArrayList<>();
        Set<Triple> allUnderlinedTriples = graph.underlines().getUnderLines();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allUnderlinedTriples.contains(new Triple(x, node, z))) {
                underlinedTriples.add(new Triple(x, node, z));
            }
        }

        return underlinedTriples;
    }

    /**
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getDottedUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> dottedUnderlinedTriples = new ArrayList<>();
        Set<Triple> allDottedUnderlinedTriples = graph.underlines().getDottedUnderlines();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allDottedUnderlinedTriples.contains(new Triple(x, node, z))) {
                dottedUnderlinedTriples.add(new Triple(x, node, z));
            }
        }

        return dottedUnderlinedTriples;
    }

    /**
     * A standard matrix graph representation for directed graphs. a[i][j] = 1
     * is j-->i and -1 if i-->j.
     *
     * @throws IllegalArgumentException if <code>graph</code> is not a directed
     *                                  graph.
     */
    private static int[][] incidenceMatrix(Graph graph) throws IllegalArgumentException {
        List<Node> nodes = graph.getNodes();
        int[][] m = new int[nodes.size()][nodes.size()];

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException("Not a directed graph.");
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                Node x1 = nodes.get(i);
                Node x2 = nodes.get(j);
                Edge edge = graph.getEdge(x1, x2);

                if (edge == null) {
                    m[i][j] = 0;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.ARROW) {
                    m[i][j] = 1;
                } else if (edge.getProximalEndpoint(x1) == Endpoint.TAIL) {
                    m[i][j] = -1;
                }
            }
        }

        return m;
    }

    public static String loadGraphRMatrix(Graph graph) throws IllegalArgumentException {
        int[][] m = GraphUtils.incidenceMatrix(graph);

        TextTable table = new TextTable(m[0].length + 1, m.length + 1);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                table.setToken(i + 1, j + 1, m[i][j] + "");
            }
        }

        for (int i = 0; i < m.length; i++) {
            table.setToken(i + 1, 0, (i + 1) + "");
        }

        List<Node> nodes = graph.getNodes();

        for (int j = 0; j < m[0].length; j++) {
            table.setToken(0, j + 1, nodes.get(j).getName());
        }

        return table.toString();
    }

    public static Graph loadGraphPcalg(File file) {
        try {
            DataSet dataSet = DataUtils.loadContinuousData(file, "//", '\"',
                    "*", true, Delimiter.COMMA);

            List<Node> nodes = dataSet.getVariables();
            Graph graph = new EdgeListGraph(nodes);

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node n1 = nodes.get(i);
                    Node n2 = nodes.get(j);

                    int e1 = dataSet.getInt(j, i);
                    int e2 = dataSet.getInt(i, j);

                    Endpoint e1a;

                    switch (e1) {
                        case 0:
                            e1a = Endpoint.NULL;
                            break;
                        case 1:
                            e1a = Endpoint.CIRCLE;
                            break;
                        case 2:
                            e1a = Endpoint.ARROW;
                            break;
                        case 3:
                            e1a = Endpoint.TAIL;
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + e1);
                    }

                    Endpoint e2a;

                    switch (e2) {
                        case 0:
                            e2a = Endpoint.NULL;
                            break;
                        case 1:
                            e2a = Endpoint.CIRCLE;
                            break;
                        case 2:
                            e2a = Endpoint.ARROW;
                            break;
                        case 3:
                            e2a = Endpoint.TAIL;
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + e1);
                    }

                    if (e1a != Endpoint.NULL && e2a != Endpoint.NULL) {
                        Edge edge = new Edge(n1, n2, e1a, e2a);
                        graph.addEdge(edge);
                    }
                }
            }

            return  graph;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    public static Graph loadGraphBNTPcMatrix(List<Node> vars, DataSet dataSet) {
        Graph graph = new EdgeListGraph(vars);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                int g = dataSet.getInt(i, j);
                int h = dataSet.getInt(j, i);

                if (g == 1 && h == 1 && !graph.isAdjacentTo(vars.get(i), vars.get(j))) {
                    graph.addUndirectedEdge(vars.get(i), vars.get(j));
                } else if (g == -1 && h == 0) {
                    graph.addDirectedEdge(vars.get(i), vars.get(j));
                }
            }
        }

        return graph;
    }

    public static String graphRMatrixTxt(Graph graph) throws IllegalArgumentException {
        int[][] m = GraphUtils.incidenceMatrix(graph);

        TextTable table = new TextTable(m[0].length + 1, m.length + 1);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                table.setToken(i + 1, j + 1, m[i][j] + "");
            }
        }

        for (int i = 0; i < m.length; i++) {
            table.setToken(i + 1, 0, (i + 1) + "");
        }

        List<Node> nodes = graph.getNodes();

        for (int j = 0; j < m[0].length; j++) {
            table.setToken(0, j + 1, nodes.get(j).getName());
        }

        return table.toString();

    }

    public static boolean containsBidirectedEdge(Graph graph) {
        boolean containsBidirected = false;

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                containsBidirected = true;
                break;
            }
        }
        return containsBidirected;
    }


    public static LinkedList<Triple> listColliderTriples(Graph graph) {
        LinkedList<Triple> colliders = new LinkedList<>();

        for (Node node : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(node);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> others = GraphUtils.asList(choice, adj);

                if (graph.isDefCollider(others.get(0), node, others.get(1))) {
                    colliders.add(new Triple(others.get(0), node, others.get(1)));
                }
            }
        }
        return colliders;
    }

    /**
     * Constructs a list of nodes from the given <code>nodes</code> list at the
     * given indices in that list.
     *
     * @param indices The indices of the desired nodes in <code>nodes</code>.
     * @param nodes   The list of nodes from which we select a sublist.
     * @return The sublist selected.
     */
    public static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<>();

        for (int index : indices) {
            list.add(nodes.get(index));
        }

        return list;
    }

    public static Set<Node> asSet(int[] indices, List<Node> nodes) {
        Set<Node> set = new HashSet<>();

        for (int i : indices) {
            set.add(nodes.get(i));
        }

        return set;
    }

    public static int numDirectionalErrors(Graph result, Graph cpdag) {
        int count = 0;

        for (Edge edge : result.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            Node _node1 = cpdag.getNode(node1.getName());
            Node _node2 = cpdag.getNode(node2.getName());

            Edge _edge = cpdag.getEdge(_node1, _node2);

            if (_edge == null) {
                continue;
            }

            if (Edges.isDirectedEdge(edge)) {
                if (_edge.pointsTowards(_node1)) {
                    count++;
                } else if (Edges.isUndirectedEdge(_edge)) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int numBidirected(Graph result) {
        int numBidirected = 0;

        for (Edge edge : result.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                numBidirected++;
            }
        }

        return numBidirected;
    }

    public static int degree(Graph graph) {
        int maxDegree = 0;

        for (Node node : graph.getNodes()) {
            int n = graph.getEdges(node).size();
            if (n > maxDegree) {
                maxDegree = n;
            }
        }

        return maxDegree;
    }

    public static String getIntersectionComparisonString(List<Graph> graphs) {
        if (graphs == null || graphs.isEmpty()) {
            return "";
        }

        StringBuilder b = GraphUtils.undirectedEdges(graphs);

        b.append(GraphUtils.directedEdges(graphs));

        return b.toString();
    }

    private static StringBuilder undirectedEdges(List<Graph> graphs) {
        List<Graph> undirectedGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            Graph graph2 = new EdgeListGraph(graph);
            graph2.reorientAllWith(Endpoint.TAIL);
            undirectedGraphs.add(graph2);
        }

        Map<String, Node> exemplars = new HashMap<>();

        for (Graph graph : undirectedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }
        List<Node> nodes = new ArrayList<>(nodeSet);
        List<Graph> undirectedGraphs2 = new ArrayList<>();

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = GraphUtils.replaceNodes(undirectedGraphs.get(i), nodes);
            undirectedGraphs2.add(graph);
        }

        Set<Edge> undirectedEdgesSet = new HashSet<>();

        for (Graph graph : undirectedGraphs2) {
            undirectedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> undirectedEdges = new ArrayList<>(undirectedEdgesSet);

        undirectedEdges.sort((o1, o2) -> {
            String name11 = o1.getNode1().getName();
            String name12 = o1.getNode2().getName();
            String name21 = o2.getNode1().getName();
            String name22 = o2.getNode2().getName();

            int major = name11.compareTo(name21);
            int minor = name12.compareTo(name22);

            if (major == 0) {
                return minor;
            } else {
                return major;
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < graphs.size(); i++) {
            groups.add(new ArrayList<>());
        }

        for (Edge edge : undirectedEdges) {
            int count = 0;

            for (Graph graph : undirectedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count++;
                }
            }

            if (count == 0) {
                throw new IllegalArgumentException();
            }

            groups.get(count - 1).add(edge);
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nIn ").append(i + 1).append(" graph").append((i > 0) ? "s" : "").append("...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n").append(j + 1).append(". ").append(groups.get(i).get(j));
            }
        }

        return b;
    }

    private static StringBuilder directedEdges(List<Graph> directedGraphs) {
        Set<Edge> directedEdgesSet = new HashSet<>();

        Map<String, Node> exemplars = new HashMap<>();

        for (Graph graph : directedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }

        List<Node> nodes = new ArrayList<>(nodeSet);

        List<Graph> directedGraphs2 = new ArrayList<>();

        for (Graph directedGraph : directedGraphs) {
            Graph graph = GraphUtils.replaceNodes(directedGraph, nodes);
            directedGraphs2.add(graph);
        }

        for (Graph graph : directedGraphs2) {
            directedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> directedEdges = new ArrayList<>(directedEdgesSet);

        directedEdges.sort((o1, o2) -> {
            String name11 = o1.getNode1().getName();
            String name12 = o1.getNode2().getName();
            String name21 = o2.getNode1().getName();
            String name22 = o2.getNode2().getName();

            int major = name11.compareTo(name21);
            int minor = name12.compareTo(name22);

            if (major == 0) {
                return minor;
            } else {
                return major;
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < directedGraphs2.size(); i++) {
            groups.add(new ArrayList<>());
        }
        Set<Edge> contradicted = new HashSet<>();
        Map<Edge, Integer> directionCounts = new HashMap<>();

        for (Edge edge : directedEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            int count1 = 0;
            int count2 = 0;

            for (Graph graph : directedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count1++;
                } else if (graph.containsEdge(edge.reverse())) {
                    count2++;
                }
            }

            if (count1 != 0 && count2 != 0 && !contradicted.contains(edge.reverse())) {
                contradicted.add(edge);
            }

            directionCounts.put(edge, count1);
            directionCounts.put(edge.reverse(), count2);

            if (count1 == 0) {
                groups.get(count2 - 1).add(edge);
            }

            if (count2 == 0) {
                groups.get(count1 - 1).add(edge);
            }
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nUncontradicted in ").append(i + 1).append(" graph").append((i > 0) ? "s" : "").append("...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n").append(j + 1).append(". ").append(groups.get(i).get(j));
            }
        }

        b.append("\n\nContradicted:\n");
        int index = 1;

        for (Edge edge : contradicted) {
            b.append("\n").append(index++).append(". ").append(Edges.undirectedEdge(edge.getNode1(), edge.getNode2())).append(" (--> ").append(directionCounts.get(edge)).append(" &lt;-- ").append(directionCounts.get(edge.reverse())).append(")");
        }

        return b;
    }

    private static boolean uncontradicted(Edge edge1, Edge edge2) {
        if (edge1 == null || edge2 == null) {
            return true;
        }

        Node x = edge1.getNode1();
        Node y = edge1.getNode2();

        if (edge1.pointsTowards(x) && edge2.pointsTowards(y)) {
            return false;
        } else return !edge1.pointsTowards(y) || !edge2.pointsTowards(x);
    }

    public static String edgeMisclassifications(double[][] counts, NumberFormat nf) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "&lt;-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "&lt;--");
        table2.setToken(7, 0, "&lt;->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "&lt;->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) {
                    table2.setToken(7 + 1, 5 + 1, "*");
                } else {
                    table2.setToken(i + 1, j + 1, "" + nf.format(counts[i][j]));
                }
            }
        }

        builder.append(table2);

        double correctEdges = 0;
        double estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = ").append(nf2.format((correctEdges / estimatedEdges)));

        return builder.toString();
    }

    public static String edgeMisclassifications(int[][] counts) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "&lt;-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) {
                    table2.setToken(7 + 1, 5 + 1, "*");
                } else {
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
                }
            }
        }

        builder.append(table2);

        int correctEdges = 0;
        int estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = ").append(nf2.format((correctEdges / (double) estimatedEdges)));

        return builder.toString();
    }

    public static void addPagColoring(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            edge.getProperties().clear();

            if (!Edges.isDirectedEdge(edge)) {
                continue;
            }

            Node x = Edges.getDirectedEdgeTail(edge);
            Node y = Edges.getDirectedEdgeHead(edge);

            graph.removeEdge(edge);
            graph.addEdge(edge);

            Edge xyEdge = graph.getEdge(x, y);
            graph.removeEdge(xyEdge);

            if (!new Paths(graph).existsSemiDirectedPath(x, y)) {
                edge.addProperty(Property.dd); // green.
            } else {
                edge.addProperty(Property.pd); // blue
            }

            graph.addEdge(xyEdge);

            if (graph.paths().defVisible(edge)) {
                edge.addProperty(Property.nl); // solid.
            } else {
                edge.addProperty(Property.pl); // dashed
            }
        }
    }


    public static int[][] edgeMisclassificationCounts(Graph leftGraph, Graph topGraph, boolean print) {
//        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

        class CountTask extends RecursiveTask<Counts> {

            private final List<Edge> edges;
            private final Graph leftGraph;
            private final Graph topGraph;
            private final Counts counts;
            private final int[] count;
            private final int chunk;
            private final int from;
            private final int to;

            public CountTask(int chunk, int from, int to, List<Edge> edges, Graph leftGraph, Graph topGraph, int[] count) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.edges = edges;
                this.leftGraph = leftGraph;
                this.topGraph = topGraph;
                this.counts = new Counts();
                this.count = count;
            }

            @Override
            protected Counts compute() {
                int range = this.to - this.from;

                if (range <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        int j = ++this.count[0];

                        Edge edge = this.edges.get(i);

                        Node x = edge.getNode1();
                        Node y = edge.getNode2();

                        Edge left = this.leftGraph.getEdge(x, y);
                        Edge top = this.topGraph.getEdge(x, y);

                        int m = GraphUtils.getTypeLeft(left, top);
                        int n = GraphUtils.getTypeTop(top);

                        this.counts.increment(m, n);
                    }

                    return this.counts;
                } else {
                    int mid = (this.to + this.from) / 2;
                    CountTask left = new CountTask(this.chunk, this.from, mid, this.edges, this.leftGraph, this.topGraph, this.count);
                    CountTask right = new CountTask(this.chunk, mid, this.to, this.edges, this.leftGraph, this.topGraph, this.count);

                    left.fork();
                    Counts rightAnswer = right.compute();
                    Counts leftAnswer = left.join();

                    leftAnswer.addAll(rightAnswer);
                    return leftAnswer;
                }
            }

            public Counts getCounts() {
                return this.counts;
            }
        }

        Set<Edge> edgeSet = new HashSet<>();
        edgeSet.addAll(topGraph.getEdges());
        edgeSet.addAll(leftGraph.getEdges());

//        System.out.println("Union formed");
        if (print) {
            System.out.println("Top graph " + topGraph.getEdges().size());
            System.out.println("Left graph " + leftGraph.getEdges().size());
            System.out.println("All edges " + edgeSet.size());
        }

        List<Edge> edges = new ArrayList<>(edgeSet);

//        System.out.println("Finding pool");
        ForkJoinPoolInstance pool = ForkJoinPoolInstance.getInstance();

//        System.out.println("Starting count task");
        CountTask task = new CountTask(500, 0, edges.size(), edges, leftGraph, topGraph, new int[1]);
        Counts counts = pool.getPool().invoke(task);

//        System.out.println("Finishing count task");
        return counts.countArray();
    }

    private static Set<Edge> complement(Set<Edge> edgeSet, Graph topGraph) {
        Set<Edge> complement = new HashSet<>(edgeSet);
        complement.removeAll(topGraph.getEdges());
        return complement;
    }

    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        if (Edges.isUndirectedEdge(edgeTop)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeTop)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeTop)) {
            return 2;
        }

        if (Edges.isDirectedEdge(edgeTop)) {
            return 3;
        }

        if (Edges.isBidirectedEdge(edgeTop)) {
            return 4;
        }

        return 5;

//        throw new IllegalArgumentException("Unsupported edge type : " + edgeTop);
    }

    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        if (edgeTop == null) {
            edgeTop = edgeLeft;
        }

        if (Edges.isUndirectedEdge(edgeLeft)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeLeft)) {
            return 1;
        }

        Node x = edgeLeft.getNode1();
        Node y = edgeLeft.getNode2();

        if (Edges.isPartiallyOrientedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) || (edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x))) {
                return 3;
            } else {
                return 2;
            }
        }

        if (Edges.isDirectedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) || (edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x))) {
                return 5;
            } else {
                return 4;
            }
        }

        if (Edges.isBidirectedEdge(edgeLeft)) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    private static int getTypeLeft2(Edge edgeLeft) {
        if (edgeLeft == null) {
            return 7;
        }

        if (Edges.isUndirectedEdge(edgeLeft)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeLeft)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeLeft)) {
            return 2;
        }

        if (Edges.isDirectedEdge(edgeLeft)) {
            return 4;
        }

        if (Edges.isBidirectedEdge(edgeLeft)) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    public static Set<Set<Node>> maximalCliques(Graph graph, List<Node> nodes) {
        Set<Set<Node>> report = new HashSet<>();
        GraphUtils.brokKerbosh1(new HashSet<>(), new HashSet<>(nodes), new HashSet<>(), report, graph);
        return report;
    }

    private static void brokKerbosh1(Set<Node> R, Set<Node> P, Set<Node> X, Set<Set<Node>> report, Graph graph) {
        if (P.isEmpty() && X.isEmpty()) {
            report.add(new HashSet<>(R));
        }

        for (Node v : new HashSet<>(P)) {
            Set<Node> _R = new HashSet<>(R);
            Set<Node> _P = new HashSet<>(P);
            Set<Node> _X = new HashSet<>(X);
            _R.add(v);
            _P.retainAll(graph.getAdjacentNodes(v));
            _X.retainAll(graph.getAdjacentNodes(v));
            GraphUtils.brokKerbosh1(_R, _P, _X, report, graph);
            P.remove(v);
            X.add(v);
        }
    }

    public static String graphToText(Graph graph) {
        // add edge properties relating to edge coloring of PAGs
        if (SearchGraphUtils.isLegalPag(graph).isLegalPag()) {
            GraphUtils.addPagColoring(graph);
        }

        Formatter fmt = new Formatter();
        fmt.format("%s%n%n", GraphUtils.graphNodesToText(graph, "Graph Nodes:", ';'));
        fmt.format("%s%n", GraphUtils.graphEdgesToText(graph, "Graph Edges:"));

        // Graph Attributes
        String graphAttributes = GraphUtils.graphAttributesToText(graph, "Graph Attributes:");
        if (graphAttributes != null) {
            fmt.format("%s%n", graphAttributes);
        }

        // Nodes Attributes
        String graphNodeAttributes = GraphUtils.graphNodeAttributesToText(graph, "Graph Node Attributes:", ';');
        if (graphNodeAttributes != null) {
            fmt.format("%s%n", graphNodeAttributes);
        }

        Set<Triple> ambiguousTriples = graph.underlines().getAmbiguousTriples();
        if (!ambiguousTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(ambiguousTriples, "Ambiguous triples (i.e. list of triples for which there is ambiguous data about whether they are colliders or not):"));
        }

        Set<Triple> underLineTriples = graph.underlines().getUnderLines();
        if (!underLineTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(underLineTriples, "Underline triples:"));
        }

        Set<Triple> dottedUnderLineTriples = graph.underlines().getDottedUnderlines();
        if (!dottedUnderLineTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(dottedUnderLineTriples, "Dotted underline triples:"));
        }

        return fmt.toString();
    }

    public static String graphNodeAttributesToText(Graph graph, String title, char delimiter) {
        List<Node> nodes = graph.getNodes();

        Map<String, Map<String, Object>> graphNodeAttributes = new LinkedHashMap<>();
        for (Node node : nodes) {
            Map<String, Object> attributes = node.getAllAttributes();

            if (!attributes.isEmpty()) {
                for (String key : attributes.keySet()) {
                    Object value = attributes.get(key);

                    Map<String, Object> nodeAttributes = graphNodeAttributes.get(key);
                    if (nodeAttributes == null) {
                        nodeAttributes = new LinkedHashMap<>();
                    }
                    nodeAttributes.put(node.getName(), value);

                    graphNodeAttributes.put(key, nodeAttributes);
                }
            }
        }

        if (!graphNodeAttributes.isEmpty()) {
            StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s", title));

            for (String key : graphNodeAttributes.keySet()) {
                Map<String, Object> nodeAttributes = graphNodeAttributes.get(key);
                int size = nodeAttributes.size();
                int count = 0;

                sb.append(String.format("%n%s: [", key));

                for (String nodeName : nodeAttributes.keySet()) {
                    Object value = nodeAttributes.get(nodeName);

                    sb.append(String.format("%s: %s", nodeName, value));

                    count++;

                    if (count < size) {
                        sb.append(delimiter);
                    }

                }

                sb.append("]");
            }

            return sb.toString();
        }

        return null;
    }

    public static String graphAttributesToText(Graph graph, String title) {
        Map<String, Object> attributes = graph.getAllAttributes();
        if (!attributes.isEmpty()) {
            StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

            for (String key : attributes.keySet()) {
                Object value = attributes.get(key);

                sb.append(key);
                sb.append(": ");
                if (value instanceof String) {
                    sb.append(value);
                } else if (value instanceof Number) {
                    sb.append(String.format("%f%n", ((Number) value).doubleValue()));
                }
            }

            return sb.toString();
        }

        return null;
    }

    public static String graphNodesToText(Graph graph, String title, char delimiter) {
        StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

        List<Node> nodes = graph.getNodes();
        int size = nodes.size();
        int count = 0;
        for (Node node : nodes) {
            count++;

            if (node.getNodeType() == NodeType.LATENT) {
                sb.append("(").append(node.getName()).append(")");
            } else {
                sb.append(node.getName());
            }

            if (count < size) {
                sb.append(delimiter);
            }
        }

        return sb.toString();
    }

    public static String graphEdgesToText(Graph graph, String title) {
        Formatter fmt = new Formatter();

        if (title != null && title.length() > 0) {
            fmt.format("%s%n", title);
        }

        List<Edge> edges = new ArrayList<>(graph.getEdges());

        Edges.sortEdges(edges);

        int count = 0;

        for (Edge edge : edges) {
            count++;

            // We will print edge's properties in the edge (via toString() function) level.
            //List<Edge.Property> properties = edge.getProperties();
            final String f = "%d. %s";
            Object[] o = new Object[2 /*+ properties.size()*/];/*+ properties.size()*/// <- here we include its properties (nl dd pl pd)
            o[0] = count;
            o[1] = edge; // <- here we include its properties (nl dd pl pd)
            fmt.format(f, o);
            fmt.format("\n");
        }

        return fmt.toString();
    }

    public static String triplesToText(Set<Triple> triples, String title) {
        Formatter fmt = new Formatter();

        if (title != null && title.length() > 0) {
            fmt.format("%s%n", title);
        }

        int size = (triples == null) ? 0 : triples.size();
        if (size > 0) {
            int count = 0;
            for (Triple triple : triples) {
                count++;
                if (count < size) {
                    fmt.format("%s%n", triple);
                } else {
                    fmt.format("%s", triple);
                }
            }
        }

        return fmt.toString();
    }

    public static TwoCycleErrors getTwoCycleErrors(Graph trueGraph, Graph estGraph) {
        Set<Edge> trueEdges = trueGraph.getEdges();
        Set<Edge> trueTwoCycle = new HashSet<>();

        for (Edge edge : trueEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (trueEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                trueTwoCycle.add(undirEdge);
            }
        }

        Set<Edge> estEdges = estGraph.getEdges();
        Set<Edge> estTwoCycle = new HashSet<>();

        for (Edge edge : estEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (estEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                estTwoCycle.add(undirEdge);
            }
        }

        Graph trueTwoCycleGraph = new EdgeListGraph(trueGraph.getNodes());

        for (Edge edge : trueTwoCycle) {
            trueTwoCycleGraph.addEdge(edge);
        }

        Graph estTwoCycleGraph = new EdgeListGraph(estGraph.getNodes());

        for (Edge edge : estTwoCycle) {
            estTwoCycleGraph.addEdge(edge);
        }

        estTwoCycleGraph = GraphUtils.replaceNodes(estTwoCycleGraph, trueTwoCycleGraph.getNodes());

        int adjFn = GraphUtils.countAdjErrors(trueTwoCycleGraph, estTwoCycleGraph);
        int adjFp = GraphUtils.countAdjErrors(estTwoCycleGraph, trueTwoCycleGraph);

        Graph undirectedGraph = GraphUtils.undirectedGraph(estTwoCycleGraph);
        int adjCorrect = undirectedGraph.getNumEdges() - adjFp;

        return new TwoCycleErrors(adjCorrect, adjFn, adjFp);
    }

    private static Set<Triple> colliders(Node b, Graph graph, Set<Triple> colliders) {
        Set<Triple> _colliders = new HashSet<>();

        for (Triple collider : colliders) {
            if (graph.paths().isAncestorOf(collider.getY(), b)) {
                _colliders.add(collider);
            }
        }

        return _colliders;
    }



    public static int getDegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getAdjacentNodes(node).size();
            }
        }

        return max;
    }

    public static int getIndegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getIndegree(node);
            }
        }

        return max;
    }
    // Used to find semidirected paths for cycle checking.
    public static Node traverseSemiDirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL || edge.getEndpoint1() == Endpoint.CIRCLE) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL || edge.getEndpoint2() == Endpoint.CIRCLE) {
                return edge.getNode1();
            }
        }
        return null;
    }

    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        String type = params.getString("graphComparisonType");

        if ("DAG".equals(type)) {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        } else if ("CPDAG".equals(type)) {
            params.set("graphComparisonType", "CPDAG");
            return SearchGraphUtils.cpdagForDag(graph);
        } else if ("PAG".equals(type)) {
            params.set("graphComparisonType", "PAG");
            return dagToPag(graph);
        } else {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        }
    }

    /**
     * The extra edge removal step for GFCI. This removed edges in triangles in the reference graph by looking
     * for sepsets for edge a--b among the adjacents of a or the adjacents of b.
     *
     * @param graph          The graph being operated on and changed.
     * @param referenceCpdag The reference graph, a CPDAG or a DAG obtained using such an algorithm.
     * @param nodes          The nodes in the graph.
     * @param sepsets        A SepsetProducer that will do the sepset search operation described.
     */
    public static void gfciExtraEdgeRemovalStep(Graph graph, Graph referenceCpdag, List<Node> nodes,
                                                SepsetProducer sepsets) {
        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = referenceCpdag.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c) && referenceCpdag.isAdjacentTo(a, c)) {
                    List<Node> sepset = sepsets.getSepset(a, c);
                    if (sepset != null) {
                        graph.removeEdge(a, c);
                    }
                }
            }
        }
    }

    /**
     * Retains only the unshielded colliders of the given graph.
     *
     * @param graph The graph to retain unshielded colliders in.
     */
    public static void retainUnshieldedColliders(Graph graph, Knowledge knowledge) {
        Graph orig = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
                    if (FciOrient.isArrowpointAllowed(a, b, graph, knowledge)
                            && FciOrient.isArrowpointAllowed(c, b, graph, knowledge)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    public static void addForbiddenReverseEdgesForDirectedEdges(Graph graph, Knowledge knowledge) {
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;
                if (graph.paths().isAncestorOf(x, y)) {
                    knowledge.setForbidden(y.getName(), x.getName());
                }
            }
        }
    }

    public static void removeNonSkeletonEdges(Graph graph, Knowledge knowledge) {
        List<Node> nodes = graph.getNodes();

        int numOfNodes = nodes.size();
        for (int i = 0; i < numOfNodes; i++) {
            for (int j = i + 1; j < numOfNodes; j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);

                if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                    continue;
                }

                if (!graph.isAdjacentTo(n1, n2)) {
                    if (!knowledge.isForbidden(n1.getName(), n2.getName())) {
                        knowledge.setForbidden(n1.getName(), n2.getName());
                    }

                    if (!knowledge.isForbidden(n2.getName(), n1.getName())) {
                        knowledge.setForbidden(n2.getName(), n1.getName());
                    }
                }
            }
        }
    }

    public static boolean compatible(Edge edge1, Edge edge2) {
        if (edge1 == null || edge2 == null) return true;

        Node x = edge1.getNode1();
        Node y = edge1.getNode2();

        Endpoint ex1 = edge1.getProximalEndpoint(x);
        Endpoint ey1 = edge1.getProximalEndpoint(y);

        Endpoint ex2 = edge2.getProximalEndpoint(x);
        Endpoint ey2 = edge2.getProximalEndpoint(y);

        return (ex1 == Endpoint.CIRCLE || (ex1 == ex2 || ex2 == Endpoint.CIRCLE)) && (ey1 == Endpoint.CIRCLE || (ey1 == ey2 || ey2 == Endpoint.CIRCLE));
    }

    public static Set<Node> pagMb(Node x, Graph G) {
        Set<Node> mb = new HashSet<>();

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);
        mb.add(x);

        for (Node d : G.getAdjacentNodes(x)) {
            mbVisit(d, path, G, mb);
        }

        mb.remove(x);

        return mb;
    }

    public static void mbVisit(Node c, LinkedList<Node> path, Graph G, Set<Node> mb) {
        if (path.contains(c)) return;
        if (mb.contains(c)) return;
        path.add(c);

        if (path.size() >= 3) {
            Node w1 = path.get(path.size() - 3);
            Node w2 = path.get(path.size() - 2);

            if (!G.isDefCollider(w1, w2, c)) {
                path.remove(c);
                return;
            }
        }

        mb.add(c);

        for (Node d : G.getAdjacentNodes(c)) {
            mbVisit(d, path, G, mb);
        }

        path.remove(c);
    }

    public static Set<Node> district(Node x, Graph G) {
        Set<Node> district = new HashSet<>();
        Set<Node> boundary = new HashSet<>();

        for (Edge e : G.getEdges(x)) {
            if (Edges.isBidirectedEdge(e)) {
                Node other = e.getDistalNode(x);
                district.add(other);
                boundary.add(other);
            }
        }

        do {
            Set<Node> previousBoundary = new HashSet<>(boundary);
            boundary = new HashSet<>();

            for (Node x2 : previousBoundary) {
                for (Edge e : G.getEdges(x2)) {
                    if (Edges.isBidirectedEdge(e)) {
                        Node other = e.getDistalNode(x2);

                        if (!district.contains(other)) {
                            district.add(other);
                            boundary.add(other);
                        }
                    }
                }
            }
        } while (!boundary.isEmpty());

        district.remove(x);

        return district;
    }

    public static boolean isDag(Graph graph) {
        boolean allDirected = true;

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                allDirected = false;
            }
        }

        return allDirected && !graph.paths().existsDirectedCycle();
    }



    private static class Counts {

        private final int[][] counts;

        public Counts() {
            this.counts = new int[8][6];
        }

        public void increment(int m, int n) {
            this.counts[m][n]++;
        }

        public int getCount(int m, int n) {
            return this.counts[m][n];
        }

        public void addAll(Counts counts2) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 6; j++) {
                    this.counts[i][j] += counts2.getCount(i, j);
                }
            }
        }

        public int[][] countArray() {
            return this.counts;
        }
    }

    public static class GraphComparison {

        private final int[][] counts;
        private final int adjFn;
        private final int adjFp;
        private final int adjCorrect;
        private final int arrowptFn;
        private final int arrowptFp;
        private final int arrowptCorrect;

        private final double adjPrec;
        private final double adjRec;
        private final double arrowptPrec;
        private final double arrowptRec;

        private final int shd;
        private final int twoCycleFn;
        private final int twoCycleFp;
        private final int twoCycleCorrect;

        private final List<Edge> edgesAdded;
        private final List<Edge> edgesRemoved;
        private final List<Edge> edgesReorientedFrom;
        private final List<Edge> edgesReorientedTo;
        private final List<Edge> edgesAdjacencies;

        public GraphComparison(int adjFn, int adjFp, int adjCorrect, int arrowptFn, int arrowptFp,
                               int arrowptCorrect, double adjPrec, double adjRec, double arrowptPrec,
                               double arrowptRec, int shd, int twoCycleCorrect, int twoCycleFn,
                               int twoCycleFp, List<Edge> edgesAdded, List<Edge> edgesRemoved,
                               List<Edge> edgesReorientedFrom, List<Edge> edgesReorientedTo,
                               List<Edge> edgesAdjacencies, int[][] counts) {
            this.adjFn = adjFn;
            this.adjFp = adjFp;
            this.adjCorrect = adjCorrect;
            this.arrowptFn = arrowptFn;
            this.arrowptFp = arrowptFp;
            this.arrowptCorrect = arrowptCorrect;

            this.adjPrec = adjPrec;
            this.adjRec = adjRec;
            this.arrowptPrec = arrowptPrec;
            this.arrowptRec = arrowptRec;

            this.shd = shd;
            this.twoCycleCorrect = twoCycleCorrect;
            this.twoCycleFn = twoCycleFn;
            this.twoCycleFp = twoCycleFp;
            this.edgesAdded = edgesAdded;
            this.edgesRemoved = edgesRemoved;
            this.edgesReorientedFrom = edgesReorientedFrom;
            this.edgesReorientedTo = edgesReorientedTo;
            this.edgesAdjacencies = edgesAdjacencies;

            this.counts = counts;
        }

        public int getAdjFn() {
            return this.adjFn;
        }

        public int getAdjFp() {
            return this.adjFp;
        }

        public int getAdjCor() {
            return this.adjCorrect;
        }

        public int getAhdFn() {
            return this.arrowptFn;
        }

        public int getAhdFp() {
            return this.arrowptFp;
        }

        public int getAhdCor() {
            return this.arrowptCorrect;
        }

        public int getShd() {
            return this.shd;
        }

        public int getTwoCycleFn() {
            return this.twoCycleFn;
        }

        public int getTwoCycleFp() {
            return this.twoCycleFp;
        }

        public int getTwoCycleCorrect() {
            return this.twoCycleCorrect;
        }

        public List<Edge> getEdgesAdded() {
            return this.edgesAdded;
        }

        public List<Edge> getEdgesRemoved() {
            return this.edgesRemoved;
        }
        public double getAdjPrec() {
            return this.adjPrec;
        }

        public double getAdjRec() {
            return this.adjRec;
        }

        public double getAhdPrec() {
            return this.arrowptPrec;
        }

        public double getAhdRec() {
            return this.arrowptRec;
        }

        public int[][] getCounts() {
            return this.counts;
        }
    }

    public static class TwoCycleErrors {

        public int twoCycCor;
        public int twoCycFn;
        public int twoCycFp;

        public TwoCycleErrors(int twoCycCor, int twoCycFn, int twoCycFp) {
            this.twoCycCor = twoCycCor;
            this.twoCycFn = twoCycFn;
            this.twoCycFp = twoCycFp;
        }

        public String toString() {
            return "2c cor = " + this.twoCycCor + "\t" + "2c fn = " + this.twoCycFn + "\t" + "2c fp = " + this.twoCycFp;
        }

    }

}
