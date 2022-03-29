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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class SampleVcpcFastRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private IndependenceFactsModel independenceFactsModel;
    private Graph trueGraph;
//    private Vcpc vcpc = null;

    private SemPm semPm;

    private SemIm semIm;


    private Set<Edge> sfVcpcAdjacent;
    private Set<Edge> sfVcpcApparent;
    private Set<Edge> sfVcpcDefinite;
    private List<Node> sfVcpcNodes;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public SampleVcpcFastRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    public SampleVcpcFastRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public SampleVcpcFastRunner(SemImWrapper semImWrapper, Parameters params, DataWrapper dataWrapper) {
        super(dataWrapper, params, null);
        semIm = semImWrapper.getSemIm();
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public SampleVcpcFastRunner(GraphSource graphWrapper, Parameters params, IndependenceFactsModel model) {
        super(graphWrapper.getGraph(), params);
        independenceFactsModel = model;
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcFastRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public SampleVcpcFastRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public SampleVcpcFastRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public SampleVcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public SampleVcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public SampleVcpcFastRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        trueGraph = graphWrapper.getGraph();
    }

    public SampleVcpcFastRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        trueGraph = graphWrapper.getGraph();
    }

    public SampleVcpcFastRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    public SampleVcpcFastRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     * <p>
     * //     * @see edu.cmu.TestSerialization
     *
     * @see TetradSerializableUtils
     */
    public static SampleVcpcFastRunner serializableInstance() {
        return new SampleVcpcFastRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = (IKnowledge) this.getParams().get("knowledge", new Knowledge2());
        Parameters searchParams = this.getParams();

        Parameters params =
                searchParams;


        SampleVcpcFast sfvcpc = new SampleVcpcFast(this.getIndependenceTest());

        sfvcpc.setKnowledge(knowledge);
        sfvcpc.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        sfvcpc.setDepth(params.getInt("depth", -1));
        if (independenceFactsModel != null) {
            sfvcpc.setFacts(independenceFactsModel.getFacts());
        }

//        vcpc.setSemPm(semPm);
//
//        if (semPm != null) {
//            vcpc.setSemPm(getSemPm());
//        }
        sfvcpc.setSemIm(semIm);

//        if (semIm != null) {
//            vcpc.setSemIm(getEstIm());
//        }

        Graph graph = sfvcpc.search();

        if (this.getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, this.getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        this.setResultGraph(graph);
        this.setSfvcpcFields(sfvcpc);
    }

    //
    public IndependenceTest getIndependenceTest() {
        Object dataModel = this.getDataModel();

        if (dataModel == null) {
            dataModel = this.getSourceGraph();
        }

        IndTestType testType = (IndTestType) (this.getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, this.getParams(), testType);
    }


    public Graph getGraph() {
        return this.getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
//        names.add("ColliderDiscovery");
//        names.add("Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = this.getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    public Set<Edge> getAdj() {
        return new HashSet<>(sfVcpcAdjacent);
    }

    public Set<Edge> getAppNon() {
        return new HashSet<>(sfVcpcApparent);
    }

    public Set<Edge> getDefNon() {
        return new HashSet<>(sfVcpcDefinite);
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge((IKnowledge) this.getParams().get("knowledge", new Knowledge2()));
        return meekRules;
    }

    @Override
    public String getAlgorithmName() {
        return "Sample-VCPC-Fast";
    }

    public SemIm getSemIm() {
        return semIm;
    }

    public SemPm getSemPm() {
        return semPm;
    }
    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        Parameters params = this.getParams();
        if (params instanceof Parameters) {
            return params.getBoolean("aggressivelyPreventCycles", false);
        }
        return false;
    }

    private void setSfvcpcFields(SampleVcpcFast sfvcpc) {
        sfVcpcAdjacent = sfvcpc.getAdjacencies();
        sfVcpcApparent = sfvcpc.getApparentNonadjacencies();
        sfVcpcDefinite = sfvcpc.getDefiniteNonadjacencies();
        sfVcpcNodes = this.getGraph().getNodes();
    }

//    public Vcpc getVcpc() {
//        return vcpc;
//    }
}


