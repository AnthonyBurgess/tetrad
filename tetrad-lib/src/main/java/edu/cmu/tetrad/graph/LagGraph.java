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

import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * Implements a graph allowing nodes in the getModel time lag to have parents
 * taken from previous time lags. This is intended to be interpreted as a
 * repeating time series graph for purposes of simulation.
 *
 * @author Joseph Ramsey
 */
public class LagGraph implements Graph {
    static final long serialVersionUID = 23L;

    private Dag graph = new Dag();
    private final List<String> variables = new ArrayList<>();
    private final Map<String, List<Node>> laggedVariables = new HashMap<>();
    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    private Paths paths;

    // New methods.
    public boolean addVariable(String variable) {
        if (this.variables.contains(variable)) {
            return false;
        }

        for (String _variable : this.variables) {
            if (variable.equals(_variable)) {
                return false;
            }
        }

        this.variables.add(variable);
        this.laggedVariables.put(variable, new ArrayList<>());

        for (String node : this.variables) {
            List<Node> _lags = this.laggedVariables.get(node);
            GraphNode _newNode = new GraphNode(node + "." + _lags.size());
            _lags.add(_newNode);
            _newNode.setCenter(5, 5);
            addNode(_newNode);
        }

        return true;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static LagGraph serializableInstance() {
        return new LagGraph();
    }

    // Modified methods from graph.
    public boolean addDirectedEdge(Node node1, Node node2) {
        return getGraph().addDirectedEdge(node1, node2);
    }

    public boolean addNode(Node node) {
        throw new UnsupportedOperationException();
    }

    // Wrapped methods from graph.

    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addEdge(Edge edge) {
        throw new UnsupportedOperationException();
    }

    public void addPropertyChangeListener(PropertyChangeListener e) {
        getGraph().addPropertyChangeListener(e);
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    public int getDegree() {
        return getGraph().getDegree();
    }

    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    public List<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(Node node) {
        return getGraph().getDegree(node);
    }

    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node2, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return getGraph().getNodesInTo(node, n);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public boolean removeEdge(Edge edge) {
        return getGraph().removeEdge(edge);
    }

    public boolean removeEdge(Node node1, Node node2) {
        return getGraph().removeEdge(node1, node2);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    public boolean removeEdges(Collection<Edge> edges) {
        return getGraph().removeEdges(edges);
    }

    public boolean removeNode(Node node) {
        return getGraph().removeNode(node);
    }

    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
    }

    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
    }

    @Override
    public Underlines getUnderlines() {
        return graph.getUnderlines();
    }

    @Override
    public Paths paths() {
        return this.paths;
    }

    public List<Node> getCausalOrdering() {
        return getGraph().getCausalOrdering();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(Edge edge) {
        return getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(Node node) {
        return getGraph().isParameterizable(node);
    }

    public boolean isTimeLagModel() {
        return false;
    }

    public TimeLagGraph getTimeLagGraph() {
        return null;
    }

    @Override
    public List<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    private Dag getGraph() {
        return this.graph;
    }

    public void setGraph(Dag graph) {
        this.graph = graph;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

}



