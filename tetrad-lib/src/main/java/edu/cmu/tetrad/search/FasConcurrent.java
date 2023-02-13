package edu.cmu.tetrad.search;

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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implements the "fast adjacency search" used in several causal algorithm in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithm, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * This variant uses the PC-Stable modification, calculating independencies in parallel within each depth.
 * It uses a slightly different algorithm from FasStableConcurrent, probably better.
 *
 * @author Joseph Ramsey.
 */
public class FasConcurrent implements IFas {

    /**
     * The independence test. This should be appropriate to the types
     */
    private final IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;


    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The ns found during the search.
     */
    private SepsetMap sepsets = new SepsetMap();

    /**
     * Set to true if verbose output is desired.
     */
    private boolean verbose;

    /**
     * Where verbose output is sent.
     */
    private PrintStream out = System.out;

    /**
     * True if the "stable" adjustment should be made.
     */
    private boolean stable = true;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.    wd
     */
    public FasConcurrent(IndependenceTest test) {
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");

        // The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
        // x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
        Graph graph = new EdgeListGraph(this.test.getVariables());

        this.sepsets = new SepsetMap();

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }


        Map<Node, Set<Node>> adjacencies = new ConcurrentHashMap<>();
        List<Node> nodes = graph.getNodes();

        for (Node node : nodes) {
            adjacencies.put(node, new HashSet<>());
        }

        for (int d = 0; d <= _depth; d++) {
            boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, adjacencies);
            } else {
                more = searchAtDepth(d, nodes, adjacencies);
            }

            if (!more) {
                break;
            }
        }

        if (this.verbose) {
            this.out.println("Finished with search, constructing Graph...");
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        if (this.verbose) {
            this.out.println("Finished constructing Graph.");
        }

        if (this.verbose) {
            this.logger.log("info", "Finishing Fast Adjacency Search.");
        }

        return graph;
    }

    private boolean searchAtDepth0(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        if (this.verbose) {
            System.out.println("Searching at depth 0.");
        }

        List<Node> empty = Collections.emptyList();

        class Depth0Task implements Callable<Boolean> {
            private final int i;

            private Depth0Task(int i) {
                this.i = i;
            }

            public Boolean call() {
                doNodeDepth0(this.i, nodes, FasConcurrent.this.test, empty, adjacencies);
                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            tasks.add(new Depth0Task(i));
        }

        ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
            this.out.print("Task has been interrupted");
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        return freeDegree(nodes, adjacencies) > this.depth;
    }

    private boolean searchAtDepth(int depth, List<Node> nodes,
                                  Map<Node, Set<Node>> adjacencies) {
        if (this.verbose) {
            System.out.println("Searching at depth " + depth);
        }

        Map<Node, Set<Node>> adjacenciesCopy;

        if (this.stable) {
            adjacenciesCopy = new ConcurrentHashMap<>();

            for (Node node : adjacencies.keySet()) {
                adjacenciesCopy.put(node, new HashSet<>(adjacencies.get(node)));
            }
        } else {
            adjacenciesCopy = adjacencies;
        }

        new ConcurrentHashMap<>();

        for (Node node : adjacencies.keySet()) {
            adjacenciesCopy.put(node, new HashSet<>(adjacencies.get(node)));
        }

        class DepthTask implements Callable<Boolean> {
            private final int i;
            private final int depth;

            public DepthTask(int i, int depth) {
                this.i = i;
                this.depth = depth;
            }

            public Boolean call() {
                doNodeAtDepth(this.i, nodes, this.depth, FasConcurrent.this.test, adjacencies);
                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            tasks.add(new DepthTask(i, depth));
        }

        ExecutorService pool = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        try {
            pool.invokeAll(tasks);
        } catch (InterruptedException exception) {
            this.out.print("Task has been interrupted");//, dateTimeNow(), task.run.index + 1);
            Thread.currentThread().interrupt();
        }

        shutdownAndAwaitTermination(pool);

        return freeDegree(nodes, adjacencies) > depth;
    }

    @Override
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return this.test;
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    //==============================PRIVATE METHODS======================/


    private void doNodeDepth0(int i, List<Node> nodes, IndependenceTest test, List<Node> empty, Map<Node, Set<Node>> adjacencies) {
        if (this.verbose) {
            if ((i + 1) % 1000 == 0) System.out.println("i = " + (i + 1));
        }

        if (this.verbose) {
            if ((i + 1) % 100 == 0) this.out.println("Node # " + (i + 1));
        }

        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        Node x = nodes.get(i);

        for (int j = i + 1; j < nodes.size(); j++) {

            Node y = nodes.get(j);

            boolean independent;

            try {
                this.numIndependenceTests++;
                independent = test.checkIndependence(x, y, empty).independent();
            } catch (Exception e) {
                e.printStackTrace();
                independent = false;
            }

            boolean noEdgeRequired =
                    this.knowledge.noEdgeRequired(x.getName(), y.getName());


            if (independent && noEdgeRequired) {
                getSepsets().set(x, y, empty);
            } else if (!forbiddenEdge(x, y)) {
                adjacencies.get(x).add(y);
                adjacencies.get(y).add(x);
            }
        }
    }

    private void doNodeAtDepth(int i, List<Node> nodes, int depth, IndependenceTest test, Map<Node,
            Set<Node>> adjacencies) {
        if (this.verbose) {
            if ((i + 1) % 1000 == 0) System.out.println("i = " + (i + 1));
        }

        Node x = nodes.get(i);

        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        List<Node> adjx = new ArrayList<>(adjacencies.get(x));

        EDGE:
        for (Node y : adjx) {
            List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
            _adjx.remove(y);
            List<Node> ppx = possibleParents(x, _adjx, this.knowledge);

            if (ppx.size() >= depth) {
                ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    List<Node> condSet = GraphUtils.asList(choice, ppx);

                    boolean independent;

                    try {
                        this.numIndependenceTests++;
                        independent = test.checkIndependence(x, y, condSet).independent();
                    } catch (Exception e) {
                        independent = false;
                    }

                    boolean noEdgeRequired =
                            this.knowledge.noEdgeRequired(x.getName(), y.getName());

                    if (independent && noEdgeRequired) {
                        adjacencies.get(x).remove(y);
                        adjacencies.get(y).remove(x);

                        getSepsets().set(x, y, condSet);

                        continue EDGE;
                    }
                }
            }
        }
    }

    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (this.knowledge.isForbidden(name1, name2) &&
                this.knowledge.isForbidden(name2, name1)) {
            if (this.verbose) {
                this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                        "forbidden by background knowledge.");
            }

            return true;
        }

        return false;
    }

    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }


    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       Knowledge knowledge) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    @Override
    public List<Node> getNodes() {
        return null;
    }

    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return null;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * The logger, by default the empty logger.
     */
    public TetradLogger getLogger() {
        return this.logger;
    }

    public void setLogger(TetradLogger logger) {
        this.logger = logger;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public int getNumDependenceJudgments() {
        return 0;
    }

    public void setOut(PrintStream out) {
        if (out == null) throw new NullPointerException();
        this.out = out;
    }

    public PrintStream getOut() {
        return this.out;
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public void setStable(boolean stable) {
        this.stable = stable;
    }
}

