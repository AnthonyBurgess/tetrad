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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression.Result;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.LogisticRegressionRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;

/**
 * Allows the user to execute a logistic regression in the GUI. Contains a panel
 * that lets the user specify a target variable (which must be binary) and a
 * list of continuous regressors, plus a tabbed pane that includes (a) a text
 * area to show the report of the logistic regression and (b) a graph workbench
 * to show the graph of the target with significant regressors from the
 * regression as parents.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM
 * Bayes estimator
 */
public class LogisticRegressionEditor extends JPanel {

    private static final long serialVersionUID = 7779226528390174L;

    /**
     * Text area for display output.
     */
    private final JTextArea modelParameters;

    /**
     * The number formatter used for printing reports.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    public LogisticRegressionEditor(LogisticRegressionRunner regressionRunner) {
        LogisticRegressionRunner regRunner = regressionRunner;

        DataSet dataSet = (DataSet) regressionRunner.getDataModel();

        for (Node node : dataSet.getVariables()) {
            if (node instanceof DiscreteVariable) {
                DiscreteVariable v = (DiscreteVariable) node;
                if (v.getNumCategories() != 2) {
                    throw new IllegalArgumentException("Logistic regression requires a dataset in which all variables " +
                            "are either continuous or binary.");
                }
            }
        }

        GraphWorkbench workbench = new GraphWorkbench();
        modelParameters = new JTextArea();
        JButton executeButton = new JButton("Execute");

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(600, 400));
        tabbedPane.add("Model", new JScrollPane(modelParameters));
        tabbedPane.add("Output Graph", new JScrollPane(workbench));

        Parameters params = regRunner.getParams();
        RegressionParamsEditorPanel paramsPanel
                = new RegressionParamsEditorPanel(regressionRunner, params, regRunner.getDataModel(), true);

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(paramsPanel);
        b1.add(Box.createHorizontalStrut(5));
        b1.add(tabbedPane);
        b.add(b1);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(executeButton);
        b.add(buttonPanel);

        this.setLayout(new BorderLayout());
        this.add(b, BorderLayout.CENTER);

        int numModels = regressionRunner.getNumModels();

        if (numModels > 1) {
            JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < numModels; i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener((e) -> {
                regressionRunner.setModelIndex(((Integer) comp.getSelectedItem()) - 1);
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box c = Box.createHorizontalBox();
            c.add(new JLabel("Using model"));
            c.add(comp);
            c.add(new JLabel("from "));
            c.add(new JLabel(regressionRunner.getModelSourceName()));
            c.add(Box.createHorizontalGlue());

            this.add(c, BorderLayout.NORTH);
        }

        //this.modelParameters.setFont(new Font("Monospaced", Font.PLAIN, 12));
        executeButton.addActionListener((e) -> {
            regRunner.setAlpha(paramsPanel.getParams().getDouble("alpha", 0.001));
            regRunner.execute();
            //  modelParameters.setText(regRunner.getReport());
            this.print(regRunner.getResult(), regRunner.getAlpha());
            Graph outGraph = regRunner.getOutGraph();
            GraphUtils.circleLayout(outGraph, 200, 200, 150);
            GraphUtils.fruchtermanReingoldLayout(outGraph);
            workbench.setGraph(outGraph);
            TetradLogger.getInstance().log("result", modelParameters.getText());
        });
    }

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(String name) {
        String oldName = this.getName();
        super.setName(name);
        firePropertyChange("name", oldName, this.getName());
    }

    //============================== Private Methods =====================================//

    /**
     * Prints the info in the result to the text area (doesn't use the results
     * representation).
     */
    private void print(Result result, double alpha) {
        if (result == null) {
            return;
        }
        // print cases
        String text = result.getNy0() + " cases have " + result.getTarget() + " = 0; ";
        text += result.getNy1() + " cases have " + result.getTarget() + " = 1.\n\n";
        // print avgs/SD
        text += "Var\tAvg\tSD\n";
        for (int i = 1; i <= result.getNumRegressors(); i++) {
            text += result.getRegressorNames().get(i - 1) + "\t";
            text += nf.format(result.getxMeans()[i]) + "\t";
            text += nf.format(result.getxStdDevs()[i]) + "\n";
        }
        text += "\nCoefficients and Standard Errors:\n";
        text += "Var\tCoeff.\tStdErr\tProb.\tSig.\n";
        for (int i = 1; i <= result.getNumRegressors(); i++) {
            text += result.getRegressorNames().get(i - 1) + "\t";
            text += nf.format(result.getCoefs()[i]) + "\t";
            text += nf.format(result.getStdErrs()[i]) + "\t";
            text += nf.format(result.getProbs()[i]) + "\t";
            if (result.getProbs()[i] < alpha) {
                text += "*\n";
            } else {
                text += "\n";
            }
        }

        text += "\n\nIntercept = " + nf.format(result.getIntercept()) + "\n";

        modelParameters.setText(text);
    }

}
