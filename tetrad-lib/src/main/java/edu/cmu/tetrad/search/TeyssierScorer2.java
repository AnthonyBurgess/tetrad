package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;

import static java.lang.Math.floor;


/**
 * Implements a scorer extending Teyssier, M., and Koller, D. (2012). Ordering-based search: A simple and effective
 * algorithm for learning Bayesian networks. arXiv preprint arXiv:1207.1429. You give it a score function
 * and a variable ordering, and it computes the score. You can move any variable left or right, and it will
 * keep track of the score using the Teyssier and Kohler method. You can move a variable to a new position,
 * and you can bookmark a state and come back to it.
 *
 * @author josephramsey
 * @author bryanandrews
 */
public class TeyssierScorer2 {
    private final List<Node> variables;
    private final Map<Node, Integer> variablesHash;
    private final Score score;
    private Map<Object, ArrayList<Node>> bookmarkedOrders = new HashMap<>();
    private Map<Object, ArrayList<Pair>> bookmarkedScores = new HashMap<>();
    private Map<Object, Map<Node, Integer>> bookmarkedOrderHashes = new HashMap<>();
    private Map<Object, Float> bookmarkedRunningScores = new HashMap<>();
    private final Map<Node, Integer> orderHash;
    private List<Node> pi;
    private List<Pair> scores;
    private Knowledge knowledge = new Knowledge();

    private boolean useScore = true;
    private boolean useRaskuttiUhler;
    private boolean useBackwardScoring;
    private boolean cachingScores = true;
    private float runningScore = 0f;
    private int maxIndegree = -1;

    public TeyssierScorer2(TeyssierScorer2 scorer) {
        this.variables = new ArrayList<>(scorer.variables);
        this.variablesHash = new HashMap<>();

        for (Node key : scorer.variablesHash.keySet()) {
            this.variablesHash.put(key, scorer.variablesHash.get(key));
        }

        this.score = scorer.score;

        this.bookmarkedOrders = new HashMap<>();

        for (Object key : scorer.bookmarkedOrders.keySet()) {
            this.bookmarkedOrders.put(key, scorer.bookmarkedOrders.get(key));
        }

        this.bookmarkedScores = new HashMap<>();

        for (Object key : scorer.bookmarkedScores.keySet()) {
            this.bookmarkedScores.put(key, new ArrayList<>(scorer.bookmarkedScores.get(key)));
        }

        this.bookmarkedOrderHashes = new HashMap<>();

        for (Object key : scorer.bookmarkedOrderHashes.keySet()) {
            this.bookmarkedOrderHashes.put(key, new HashMap<>(scorer.bookmarkedOrderHashes.get(key)));
        }

        this.bookmarkedRunningScores = new HashMap<>(scorer.bookmarkedRunningScores);

        this.orderHash = new HashMap<>(scorer.orderHash);

        this.pi = new ArrayList<>(scorer.pi);

        this.scores = new ArrayList<>(scorer.scores);
        this.knowledge = scorer.knowledge;
        this.useScore = scorer.useScore;
        this.useRaskuttiUhler = scorer.useRaskuttiUhler;
        this.useBackwardScoring = scorer.useBackwardScoring;
        this.cachingScores = scorer.cachingScores;
        this.runningScore = scorer.runningScore;
        this.maxIndegree = scorer.maxIndegree;
    }

    public TeyssierScorer2(Score score) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        this.score = score;

        if (score != null) {
            this.variables = score.getVariables();
            this.pi = new ArrayList<>(this.variables);
        } else {
            throw new IllegalArgumentException("Need a score");
        }

        this.orderHash = new HashMap<>();
        nodesHash(this.orderHash, this.pi);

        this.variablesHash = new HashMap<>();
        nodesHash(this.variablesHash, this.variables);

        if (score instanceof GraphScore) {
            this.useScore = false;
        }
    }

    public boolean tuck(Node k, int j) {
        if (!adjacent(k, get(j))) return false;
//        if (coveredEdge(k, get(j))) return false;
        if (j >= index(k)) return false;
        int _j = j;
        int _k = index(k);

        bookmark(-55);

        Set<Node> ancestors = getAncestors(k);

        for (int i = j + 1; i <= index(k); i++) {
            if (ancestors.contains(get(i))) {
                moveToNoUpdate(get(i), j++);
            }
        }

        updateScores(_j, _k);

        return true;
    }

    /**
     * @param useScore True if the score should be used; false if the test should be used.
     */
    public void setUseScore(boolean useScore) {
        if (!(this.score instanceof GraphScore)) {
            this.useScore = useScore;
        }
    }

    /**
     * @param knowledge Knowledge of forbidden edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /**
     * Scores the given permutation. This needs to be done initially before any move or tuck
     * operations are performed.
     *
     * @param order The permutation to score.
     * @return The score of it.
     */
    public float score(List<Node> order) {
        this.pi = new ArrayList<>(order);
        this.scores = new ArrayList<>();

        for (int i1 = 0; i1 < order.size(); i1++) {
            this.scores.add(null);
        }

        clearBookmarks();
        initializeScores();
        return score();
    }

    /**
     * @return The score of the current permutation.
     */
    public float score() {
        return sum();
    }

    private float sum() {
        float score = 0;

        for (int i = 0; i < this.pi.size(); i++) {
            float score1 = this.scores.get(i).getScore();
            score += score1;
        }

        return score;
    }

    /**
     * Moves v to a new index.
     *
     * @param v       The variable to move.
     * @param toIndex The index to move v to.
     */
    public void moveTo(Node v, int toIndex) {
        int vIndex = index(v);
        if (vIndex == toIndex) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

        if (toIndex < vIndex) {
            updateScores(toIndex, vIndex);
        } else {
            updateScores(vIndex, toIndex);
        }
    }

    /**
     * Swaps m and n in the permutation.
     *
     * @param m The first variable.
     * @param n The second variable.
     * @return True iff the swap was done.
     */
    public boolean swap(Node m, Node n) {
        int i = this.orderHash.get(m);
        int j = this.orderHash.get(n);

        this.pi.set(i, n);
        this.pi.set(j, m);

        if (violatesKnowledge(this.pi)) {
            this.pi.set(i, m);
            this.pi.set(j, n);
            return false;
        }

        if (i < j) {
            updateScores(i, j);
        } else {
            updateScores(j, i);
        }

        return true;
    }

    /**
     * Returns true iff x-&gt;y or y-&gt;x is a covered edge. x-&gt;y is a covered edge if
     * parents(x) = parents(y) \ {x}
     *
     * @param x The first variable.
     * @param y The second variable.
     * @return True iff x-&gt;y or y-&gt;x is a covered edge.
     */
    public boolean coveredEdge(Node x, Node y) {
        if (!adjacent(x, y)) return false;
        Set<Node> px = getParents(x);
        Set<Node> py = getParents(y);
        px.remove(y);
        py.remove(x);
        return px.equals(py);
    }

    /**
     * @return A copy of the current permutation.
     */
    public List<Node> getPi() {
        return new ArrayList<>(this.pi);
    }

    /**
     * Return the index of v in the current permutation.
     *
     * @param v The variable.
     * @return Its index.
     */
    public int index(Node v) {
        Integer integer = this.orderHash.get(v);

        if (integer == null)
            throw new IllegalArgumentException("First 'evaluate' a permutation containing variable "
                    + v + ".");

        return integer;
    }

    /**
     * Returns the parents of the node at index p.
     *
     * @param p The index of the node.
     * @return Its parents.
     */
    public Set<Node> getParents(int p) {
        return new HashSet<>(this.scores.get(p).getParents());
    }

    /**
     * Returns the parents of a node v.
     *
     * @param v The variable.
     * @return Its parents.
     */
    public Set<Node> getParents(Node v) {
        return getParents(index(v));
    }

    /**
     * Returns the nodes adjacent to v.
     *
     * @param v The variable.
     * @return Its adjacent nodes.
     */
    public Set<Node> getAdjacentNodes(Node v) {
        Set<Node> adj = new HashSet<>();

        for (Node w : this.pi) {
            if (getParents(v).contains(w) || getParents(w).contains(v)) {
                adj.add(w);
            }
        }

        return adj;
    }

    /**
     * Returns the DAG build for the current permutation, or its CPDAG.
     *
     * @param cpDag True iff the CPDAG should be returned, False if the DAG.
     * @return This graph.
     */
    public Graph getGraph(boolean cpDag) {
        List<Node> order = getPi();
        Graph G1 = new EdgeListGraph(getPi());

        for (int p = 0; p < order.size(); p++) {
            for (Node z : getParents(p)) {
                G1.addDirectedEdge(z, order.get(p));
            }
        }

        if (cpDag) {
            return SearchGraphUtils.cpdagForDag(G1);
        } else {
            return G1;
        }
    }

    /**
     * Returns a list of adjacent node pairs in the current graph.
     *
     * @return This list.
     */
    public List<NodePair> getAdjacencies() {
        List<Node> order = getPi();
        Set<NodePair> pairs = new HashSet<>();

        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                Node x = order.get(i);
                Node y = order.get(j);

                if (adjacent(x, y)) {
                    pairs.add(new NodePair(x, y));
                }
            }
        }

        return new ArrayList<>(pairs);
    }

    public Set<Node> getAncestors(Node node) {
        Set<Node> ancestors = new HashSet<>();
        collectAncestorsVisit(node, ancestors);

        return ancestors;
    }

    private void collectAncestorsVisit(Node node, Set<Node> ancestors) {
        if (ancestors.contains(node)) {
            return;
        }

        ancestors.add(node);
        Set<Node> parents = getParents(node);

        if (!parents.isEmpty()) {
            for (Node parent : parents) {
                collectAncestorsVisit(parent, ancestors);
            }
        }
    }

    /**
     * Returns a list of edges for the current graph as a list of ordered pairs.
     *
     * @return This list.
     */
    public List<OrderedPair<Node>> getEdges() {
        List<Node> order = getPi();
        List<OrderedPair<Node>> edges = new ArrayList<>();

        for (Node y : order) {
            for (Node x : getParents(y)) {
                edges.add(new OrderedPair<>(x, y));
            }
        }

        return edges;
    }

    /**
     * @return The number of edges in the current graph.
     */
    public int getNumEdges() {
        int numEdges = 0;

        for (int p = 0; p < this.pi.size(); p++) {
            numEdges += getParents(p).size();
        }

        return numEdges;
    }

    /**
     * Returns the node at index j in pi.
     *
     * @param j The index.
     * @return The node at that index.
     */
    public Node get(int j) {
        return this.pi.get(j);
    }

    /**
     * Bookmarks the current pi as index key.
     *
     * @param key This bookmark may be retrieved using the index 'key', an integer.
     *            This bookmark will be stored until it is retrieved and then removed.
     */
    public void bookmark(Object key) {
        if (!bookmarkedOrders.containsKey(key)) {
            this.bookmarkedOrders.put(key, new ArrayList<>(this.pi));
            this.bookmarkedScores.put(key, new ArrayList<>(this.scores));
            this.bookmarkedOrderHashes.put(key, new HashMap<>(this.orderHash));
            this.bookmarkedRunningScores.put(key, runningScore);
        } else {
            List<Node> pi2 = this.bookmarkedOrders.get(key);
            List<Pair> scores2 = this.bookmarkedScores.get(key);
            Map<Node, Integer> hashes2 = this.bookmarkedOrderHashes.get(key);

            int first = 0;
            int last = size() - 1;

            for (int i = 0; i < size(); i++) {
                if (this.pi.get(i) != pi2.get(i)) {
                    first = i;
                    break;
                }
            }

            for (int i = size() - 1; i >= 0; i--) {
                if (this.pi.get(i) != pi2.get(i)) {
                    last = i;
                    break;
                }
            }

            for (int i = first; i <= last; i++) {
                pi2.set(i, pi.get(i));
                scores2.set(i, scores.get(i));
                hashes2.put(pi2.get(i), orderHash.get(pi2.get(i)));
            }

            this.bookmarkedRunningScores.put(key, runningScore);
        }
    }

    /**
     * Bookmarks the current pi with index Integer.MIN_VALUE.
     */
    public void bookmark() {
        bookmark(Integer.MIN_VALUE);
    }

    /**
     * Retrieves the bookmarked state for index 'key' and removes that bookmark.
     *
     * @param key The integer key for this bookmark.
     */
    public void goToBookmark(Object key) {
        if (!this.bookmarkedOrders.containsKey(key)) {
            throw new IllegalArgumentException("That key was not bookmarked.");
        }

        List<Node> pi2 = this.bookmarkedOrders.get(key);
        List<Pair> scores2 = this.bookmarkedScores.get(key);
        Map<Node, Integer> hashes2 = this.bookmarkedOrderHashes.get(key);
        Float runningScore2 = this.bookmarkedRunningScores.get(key);

        int first = size();
        int last = -1;

        for (int i = 0; i < size(); i++) {
            if (this.pi.get(i) != pi2.get(i)) {
                first = i;
                break;
            }
        }

        for (int i = size() - 1; i >= 0; i--) {
            if (this.pi.get(i) != pi2.get(i)) {
                last = i;
                break;
            }
        }

        for (int i = first; i <= last; i++) {
            if (this.pi.get(i) != (pi2.get(i))) {
                this.pi.set(i, pi2.get(i));
                this.scores.set(i, scores2.get(i));
                this.orderHash.put(pi.get(i), hashes2.get(pi.get(i)));
            }
        }

        this.runningScore = runningScore2;
    }

    /**
     * Retries the bookmark with key = Integer.MIN_VALUE and removes the bookmark.
     */
    public void goToBookmark() {
        goToBookmark(Integer.MIN_VALUE);
    }

    /**
     * Clears all bookmarks.
     */
    public void clearBookmarks() {
        this.bookmarkedOrders.clear();
        this.bookmarkedScores.clear();
        this.bookmarkedOrderHashes.clear();
        this.bookmarkedRunningScores.clear();
    }

    /**
     * @return The size of pi, the current permutation.
     */
    public int size() {
        return this.pi.size();
    }

    /**
     * Returns True iff a is adjacent to b in the current graph.
     *
     * @param a The first node.
     * @param b The second node.
     * @return True iff adj(a, b).
     */
    public boolean adjacent(Node a, Node b) {
        if (a == b) return false;
        return parent(a, b) || parent(b, a);
    }

    /**
     * Returns true iff [a, b, c] is a collider.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff a-&gt;b&lt;-c in the current DAG.
     */
    public boolean collider(Node a, Node b, Node c) {
        return getParents(b).contains(a) && getParents(b).contains(c);
    }

    /**
     * Returns true iff [a, b, c] is a triangle.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff adj(a, b) and adj(b, c) and adj(a, c).
     */
    public boolean triangle(Node a, Node b, Node c) {
        return adjacent(a, b) && adjacent(b, c) && adjacent(a, c);
    }

    /**
     * True iff the nodes in W form a clique in the current DAG.
     *
     * @param W The nodes.
     * @return True iff these nodes form a clique.
     */
    public boolean clique(List<Node> W) {
        for (int i = 0; i < W.size(); i++) {
            for (int j = i + 1; j < W.size(); j++) {
                if (!adjacent(W.get(i), W.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (!knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void initializeScores() {
//        for (int i1 = 0; i1 < this.pi.size(); i1++) this.prefixes.set(i1, null);
        updateScores(0, this.pi.size() - 1);
    }

    public void updateScores(int i1, int i2) {
        for (int i = i1; i <= i2; i++) {
            this.orderHash.put(this.pi.get(i), i);
            recalculate(i);
        }

//        for (int i = i1; i <= i2; i++) {
//            this.orderHash.put(this.pi.get(i), i);
//        }
//
//        int chunk = getChunkSize(i2 - i1 + 1);
//        List<MyTask> tasks = new ArrayList<>();
//
//        for (int w = 0; w < size(); w += chunk) {
//            tasks.add(new MyTask(pi, this, chunk, orderHash, w, w + chunk));
//        }
//
//        ForkJoinPool.commonPool().invokeAll(tasks);
    }

    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    public void setMaxIndegree(int maxIndegree) {
        this.maxIndegree = maxIndegree;
    }

    class MyTask implements Callable<Boolean> {
        final List<Node> pi;
        final Map<Node, Integer> orderHash;
        TeyssierScorer2 scorer;
        int chunk;
        private final int from;
        private final int to;

        MyTask(List<Node> pi, TeyssierScorer2 scorer, int chunk, Map<Node, Integer> orderHash,
               int from, int to) {
            this.pi = pi;
            this.scorer = scorer;
            this.chunk = chunk;
            this.orderHash = orderHash;
            this.from = from;
            this.to = to;
        }

        @Override
        public Boolean call() throws InterruptedException {
            for (int i = from; i <= to; i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                recalculate(i);
            }

            return true;
        }
    }


    private float score(Node n, Set<Node> pi) {
        int[] parentIndices = new int[pi.size()];

        int k = 0;

        for (Node p : pi) {
            parentIndices[k++] = this.variablesHash.get(p);
        }

        return (float) this.score.localScore(this.variablesHash.get(n), parentIndices);
    }

    public Set<Node> getPrefix(int i) {
        Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(this.pi.get(j));
        }

        return prefix;
    }

    private void recalculate(int p) {
        Pair p2 = getGrowShrinkScore(p);

        if (scores.get(p) == null) {
            this.runningScore += p2.score;
        } else {
            this.runningScore += p2.score - scores.get(p).getScore();
        }

        this.scores.set(p, p2);
    }

    private void nodesHash(Map<Node, Integer> nodesHash, List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }
    }

    @NotNull
    private Pair getGrowShrinkScore(int p) {
        Node n = this.pi.get(p);

        Set<Node> parents = new HashSet<>();
        boolean changed = true;

        float sMax = score(n, new HashSet<>());
        Set<Node> prefix1 = getPrefix(p);
        List<Node> prefix = new ArrayList<>(prefix1);

        // Backward scoring only from the prefix variables
        if (this.useBackwardScoring) {
            parents.addAll(prefix);
            sMax = score(n, parents);
            changed = false;
        }

        // Grow-shrink
        while (changed) {
            changed = false;

            // Let z be the node that maximizes the score...
            Node z = null;

            for (Node z0 : prefix) {
                if (parents.contains(z0)) continue;

                if (!knowledge.isEmpty() && this.knowledge.isForbidden(z0.getName(), n.getName())) continue;
                parents.add(z0);

                float s2 = score(n, parents);

                if (s2 > sMax) {
                    sMax = s2;
                    z = z0;
                }

                parents.remove(z0);
            }

            if (z != null) {
                parents.add(z);
                if (maxIndegree > 0 && parents.size() > maxIndegree) break;
                changed = true;
            }
        }

        boolean changed2 = true;

        while (changed2) {
            changed2 = false;

            Node w = null;

            for (Node z0 : new HashSet<>(parents)) {
                parents.remove(z0);

                float s2 = score(n, parents);

                if (s2 > sMax) {
                    sMax = s2;
                    w = z0;
                }

                parents.add(z0);
            }

            if (w != null) {
                parents.remove(w);
                changed2 = true;
            }
        }

//        this.prefixes.set(p, prefix1);

        if (this.useScore) {
            return new Pair(parents, Float.isNaN(sMax) ? Float.NEGATIVE_INFINITY : sMax);
        } else {
            return new Pair(parents, -parents.size());
        }
    }

    public Set<Set<Node>> getSkeleton() {
        List<Node> order = getPi();
        Set<Set<Node>> skeleton = new HashSet<>();

        for (Node y : order) {
            for (Node x : getParents(y)) {
                Set<Node> adj = new HashSet<>();
                adj.add(x);
                adj.add(y);
                skeleton.add(adj);
            }
        }

        return skeleton;
    }

    public void moveToNoUpdate(Node v, int toIndex) {
//        bookmark(-55);

        if (!this.pi.contains(v)) return;
        int vIndex = index(v);
        if (vIndex == toIndex) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

//        if (violatesKnowledge(this.pi)) {
//            goToBookmark(-55);
//        }
    }

    public boolean parent(Node k, Node j) {
        return getParents(j).contains(k);
    }

    public double remove(Node x) {
        Set<Node> adj = getAdjacentNodes(x);

        int index = index(x);
        this.scores.remove(index);
        this.pi.remove(x);
        this.orderHash.remove(x);
        this.variables.remove(x);
        this.variablesHash.remove(x);

        for (int i = index; i < pi.size(); i++) {
            if (adj.contains(get(i))) {
                recalculate(i);
                this.orderHash.put(this.pi.get(i), i);
            }
        }

        updateScores(index, pi.size() - 1);
        clearBookmarks();
        return score();
    }

    public static class Pair {
        private final Set<Node> parents;
        private final float score;

        private Pair(Set<Node> parents, float score) {
            this.parents = parents;
            this.score = score;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public float getScore() {
            return this.score;
        }

        public int hashCode() {
            return this.parents.hashCode() + (int) floor(10000D * this.score);
        }

        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Pair)) return false;
            Pair thatPair = (Pair) o;
            return this.parents.equals(thatPair.parents) && this.score == thatPair.score;
        }
    }
}