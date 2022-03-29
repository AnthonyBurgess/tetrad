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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ScoredGraph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.Indexable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the output DGs of the LiNG algorithm.
 *
 * @author Joseph Ramsey
 */
public class FgesDisplay extends JPanel implements GraphEditable {
    private final Graph resultGraph;
    private final GraphWorkbench workbench;
    private List<ScoredGraph> topGraphs;
    private final JSpinner spinner = new JSpinner();
    private final JLabel totalLabel;
    private final NumberFormat nf;
    private final JLabel scoreLabel;
    private Indexable indexable;

    public FgesDisplay(Graph resultGraph, List<ScoredGraph> topGraphs, Indexable indexable) {
        nf = NumberFormatUtil.getInstance().getNumberFormat();
        this.indexable = indexable;
        this.topGraphs = topGraphs;

        int numCPDAGs = topGraphs.size();

        if (topGraphs.size() == 0) {
            workbench = new GraphWorkbench();
        } else {
            workbench = new GraphWorkbench(topGraphs.get(indexable.getIndex()).getGraph());
        }

        this.resultGraph = resultGraph;

        scoreLabel = new JLabel();
        this.setCPDAG();

        SpinnerNumberModel model =
                new SpinnerNumberModel(numCPDAGs == 0 ? 1 : indexable.getIndex() + 1, 1, numCPDAGs == 0 ? 1 : numCPDAGs, 1);

        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                FgesDisplay.this.getIndexable().setIndex((Integer) model.getValue() - 1);
                FgesDisplay.this.setCPDAG();
            }
        });

//        spinner = new JSpinner();
        spinner.setModel(model);
        totalLabel = new JLabel(" of " + numCPDAGs);

        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(" Score = "));
        b1.add(scoreLabel);
        b1.add(new JLabel(" forbid_latent_common_causes "));
        b1.add(spinner);
        b1.add(totalLabel);

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(workbench);
        graphPanel.add(jScrollPane);
        b2.add(graphPanel);
        b.add(b2);

        this.setLayout(new BorderLayout());
        this.add(b, BorderLayout.CENTER);
    }

    private void setCPDAG() {
        this.setDisplayGraph();
        this.setDisplayScore();
    }

    private void setDisplayGraph() {
        int index = this.getIndexable().getIndex();

        if (topGraphs.size() == 0) {
            workbench.setGraph(new EdgeListGraph());
        } else {
            ScoredGraph scoredGraph = topGraphs.get(index);
            workbench.setGraph(scoredGraph.getGraph());
        }
    }

    private void setDisplayScore() {
        if (topGraphs.isEmpty()) {
            scoreLabel.setText("*");
        } else {
            double score = topGraphs.get(this.getIndexable().getIndex()).getScore();

            if (Double.isNaN(score)) {
                scoreLabel.setText("*");
            } else {
                scoreLabel.setText(nf.format(score));
            }
        }
    }

    private void resetDisplay() {
        int numCPDAGs = topGraphs.size();

        SpinnerNumberModel model = new SpinnerNumberModel(numCPDAGs, 0, numCPDAGs, 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                FgesDisplay.this.getIndexable().setIndex((Integer) model.getValue() - 1);
                FgesDisplay.this.setCPDAG();
            }
        });

        spinner.setModel(model);
        totalLabel.setText(" of " + numCPDAGs);

        if (numCPDAGs == 0) {
            workbench.setGraph(resultGraph);
        } else {
            workbench.setGraph(topGraphs.get(numCPDAGs - 1).getGraph());
        }

        this.setDisplayScore();
    }

    public void resetGraphs(List<ScoredGraph> topGraphs) {
        this.topGraphs = topGraphs;
        this.resetDisplay();
    }

    public List getSelectedModelComponents() {
        Component[] components = this.getWorkbench().getComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    public void pasteSubsession(List sessionElements, Point upperLeft) {
        this.getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        this.getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                this.getWorkbench().selectNode(modelNode);
            }
        }

        this.getWorkbench().selectConnectingEdges();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Graph getGraph() {
        return workbench.getGraph();
    }

    public void setGraph(Graph graph) {
        workbench.setGraph(graph);
    }

    public List<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    private Indexable getIndexable() {
        return indexable;
    }

    private void setIndexable(Indexable indexable) {
        this.indexable = indexable;
    }
}


