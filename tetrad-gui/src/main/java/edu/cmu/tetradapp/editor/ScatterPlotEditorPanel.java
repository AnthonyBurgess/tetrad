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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Michael Freenor
 */
class ScatterPlotEditorPanel extends JPanel {


    /**
     * Combo box of all the variables.
     */
    private final JComboBox yVariableBox;
    private final JComboBox xVariableBox;
    public final JComboBox newCondBox;

    /**
     * The dataset being viewed.
     */
    public final DataSet dataSet;

    private ScatterPlotOld scatterPlot;

    Vector boxes; //check boxes that activate the use of conditional variables
    private final JCheckBox regressionBox; //check box that enables the drawing of the regression line
    Vector granularity; //text fields containing the resolution of our conditional variables
    Vector slideLabels; //displays information about the conditional variables used, such as the interval being used
    Vector scrollers;  //the actual thumb-scrollers used to adjust the conditional variables
    Vector condVariables; //stores the conditional variables

    /**
     * Constructs the editor panel given the initial scatter plot and the dataset.
     */
    public ScatterPlotEditorPanel(ScatterPlotOld scatterPlot, DataSet dataSet) {
        //   construct components
        this.regressionBox = new JCheckBox();
        this.setLayout(new BorderLayout());
        // first build scatter plot and components used in the editor.
        this.scatterPlot = scatterPlot;
        Node selected = scatterPlot.getYVariable();
        this.dataSet = dataSet;
        this.yVariableBox = new JComboBox();
        this.xVariableBox = new JComboBox();
        ListCellRenderer renderer = new VariableBoxRenderer();
        this.yVariableBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.yVariableBox.addItem(node);
                if (node == selected) {
                    this.yVariableBox.setSelectedItem(node);
                }
            }
        }

        this.xVariableBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.xVariableBox.addItem(node);
                if (node == selected) {
                    this.xVariableBox.setSelectedItem(node);
                }
            }
        }

        this.newCondBox = new JComboBox();
        this.newCondBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.newCondBox.addItem(node);
                if (node == selected) {
                    this.newCondBox.setSelectedItem(node);
                }
            }
        }

        // build the gui.
        this.add(buildEditArea(dataSet));
    }

    private void changeScatterPlot(ScatterPlotOld scatterPlot) {
        this.scatterPlot = scatterPlot;
        // fire event
        this.firePropertyChange("histogramChange", null, scatterPlot);
    }

    public static void setPreferredAsMax(JComponent component) {
        component.setMaximumSize(component.getPreferredSize());

    }

    private Box buildEditArea(DataSet dataset) {
        ScatterPlotEditorPanel.setPreferredAsMax(this.yVariableBox);
        ScatterPlotEditorPanel.setPreferredAsMax(this.xVariableBox);
        ScatterPlotEditorPanel.setPreferredAsMax(this.newCondBox);

        Box main2 = Box.createVerticalBox();

        Box main = Box.createVerticalBox();

        Box hBox2 = Box.createHorizontalBox();
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(new JLabel("Select Variable for X-Axis: "));
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(this.xVariableBox);
        hBox2.add(Box.createHorizontalGlue());
        main.add(hBox2);

        Box hBox = Box.createHorizontalBox();
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(new JLabel("Select Variable for Y-Axis: "));
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(this.yVariableBox);
        hBox.add(Box.createHorizontalGlue());
        main.add(hBox);


        this.xVariableBox.addActionListener(new ScatterListener(this));
        this.yVariableBox.addActionListener(new ScatterListener(this));

        Box hBox6 = Box.createHorizontalBox();
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(new JLabel("Display Regression Line: "));
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(this.regressionBox);
        hBox6.add(Box.createHorizontalGlue());
        main.add(hBox6);

        this.regressionBox.addActionListener(new ScatterListener(this));


        JButton newCond = new JButton("Add New Conditional Variable");
        Box hBox3 = Box.createHorizontalBox();
        hBox3.add(Box.createHorizontalStrut(10));
        this.newCondBox.setPreferredSize(new Dimension(50, 20));
        hBox3.add(this.newCondBox);
        hBox3.add(Box.createHorizontalStrut(10));
        hBox3.add(newCond);
        main.add(hBox3);


        newCond.addActionListener(new AddVariableListener(main, this));

        this.boxes = new Vector();
        this.granularity = new Vector();
        this.slideLabels = new Vector();
        this.scrollers = new Vector();
        this.condVariables = new Vector();

        main2.add(main);
        //main2.add(Box.createVerticalStrut(10));
        main2.add(Box.createVerticalGlue());

        return main2;
    }

    /**
     * Redraws the scatter plot.
     */
    public void redrawScatterPlot() {
        ScatterPlotOld newPlot = new ScatterPlotOld(this.scatterPlot.getDataSet(), (ContinuousVariable) (this.yVariableBox.getSelectedItem()),
                (ContinuousVariable) (this.xVariableBox.getSelectedItem()));
        if (this.regressionBox.isSelected())
            newPlot.setDrawRegLine(true);
        for (int i = 0; i < this.scrollers.size(); i++) {
            boolean breakNow = false;
            //if(((JCheckBox)boxes.get(i)).isSelected())
            //{
            double low = ((JScrollBar) this.scrollers.get(i)).getValue();
            double high = ((JScrollBar) this.scrollers.get(i)).getValue() + ((JScrollBar) this.scrollers.get(i)).getVisibleAmount();
            if (low > high) breakNow = true;

            ContinuousVariable currentNode = (ContinuousVariable) (this.condVariables.get(i));
            int variableIndex = newPlot.getDataSet().getColumn(currentNode);

            //edit the index set here
            Vector newIndexSet = new Vector();
            Vector newComplementSet = new Vector();
            for (int j = 0; j < newPlot.getIndexSet().size(); j++) {
                int currentIndex = (Integer) newPlot.getIndexSet().get(j);
                //lookup value at this index
                double value = newPlot.getDataSet().getDouble(currentIndex, variableIndex);
                //check if value is in the right interval -- if so we add to the new indexSet
                if (value >= low && value <= high) {
                    newIndexSet.add(currentIndex);
                } else {
                    newComplementSet.add(currentIndex);
                }
            }
            newPlot.setIndexSet(newIndexSet);
            newPlot.setComplementIndexSet(newComplementSet);
            //}
            if (breakNow) break;
        }

        changeScatterPlot(newPlot);
    }

    //========================== Inner classes ===========================//


    private static class VariableBoxRenderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Node node = (Node) value;
            if (node == null) {
                this.setText("");
            } else {
                this.setText(node.getName());
            }
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }


}

class SliderListener implements AdjustmentListener {
    private final ScatterPlotEditorPanel sp;
    private final int index;

    public SliderListener(ScatterPlotEditorPanel sp, int index) {
        this.sp = sp;
        this.index = index;
    }

    public void adjustmentValueChanged(AdjustmentEvent evt) {
        this.sp.redrawScatterPlot();
        ((JLabel) this.sp.slideLabels.get(this.index)).setText("Viewing Range: " +
                "[" + ((JScrollBar) this.sp.scrollers.get(this.index)).getValue() + ", " +
                (((JScrollBar) this.sp.scrollers.get(this.index)).getValue() +
                        ((JScrollBar) this.sp.scrollers.get(this.index)).getVisibleAmount()) + "]");
    }
}

class GranularityListener implements FocusListener, ActionListener {
    private final ScatterPlotEditorPanel sp;
    private final int index;

    public GranularityListener(ScatterPlotEditorPanel sp, int index) {
        this.sp = sp;
        this.index = index;
    }

    public void focusGained(FocusEvent evt) {
    }

    public void actionPerformed(ActionEvent evt) {
        focusLost(null);
    }

    public void focusLost(FocusEvent evt) {
        JScrollBar currentBar = ((JScrollBar) this.sp.scrollers.get(this.index));
        currentBar.setValue((int) FastMath.floor(currentBar.getMinimum()));
        int newVisibleAmount = (int) Double.parseDouble(((JTextField) this.sp.granularity.get(this.index)).getText());
        if (newVisibleAmount > FastMath.ceil(currentBar.getMaximum()) - FastMath.floor(currentBar.getMinimum()))
            newVisibleAmount = (int) (FastMath.ceil(currentBar.getMaximum()) - FastMath.floor(currentBar.getMinimum()));
        currentBar.setVisibleAmount(newVisibleAmount);
        ((JLabel) this.sp.slideLabels.get(this.index)).setText("Viewing Range: [" + currentBar.getValue() +
                ", " + (currentBar.getValue() + currentBar.getVisibleAmount()) + "]");
    }
}

class ScatterListener implements ActionListener {
    private final ScatterPlotEditorPanel sp;

    public ScatterListener(ScatterPlotEditorPanel sp) {
        this.sp = sp;
    }

    public void actionPerformed(ActionEvent evt) {
        this.sp.redrawScatterPlot();
    }
}

/*
    This class listens to the "Add New Conditional Variable" button.
    It adds more components to the editor panel to allow the user to tweak
    conditional variables.  
 */
class AddVariableListener implements ActionListener {
    private final ScatterPlotEditorPanel sp;
    private final Box main;

    public AddVariableListener(Box main, ScatterPlotEditorPanel sp) {
        this.sp = sp;
        this.main = main;
    }

    public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < this.sp.boxes.size(); i++) {
            if (((Node) this.sp.newCondBox.getSelectedItem()).getName().equals(((Node) this.sp.condVariables.get(i)).getName())) {
                return;
            }
        }

        int i = this.sp.boxes.size();
        Box hBox4 = Box.createHorizontalBox();
        hBox4.add(Box.createHorizontalStrut(10));
        this.sp.boxes.add(new JCheckBox());
        JButton removeButton = new JButton("Remove " + ((Node) this.sp.newCondBox.getSelectedItem()).getName());
        //hBox4.add((JCheckBox)sp.boxes.get(i));
        hBox4.add(Box.createHorizontalStrut(10));
        this.sp.condVariables.add(this.sp.newCondBox.getSelectedItem());
        hBox4.add(new JLabel(((Node) this.sp.newCondBox.getSelectedItem()).getName() + ": "));
        //hBox4.add(Box.createHorizontalStrut(10));
        ((JCheckBox) this.sp.boxes.get(i)).addActionListener(new ScatterListener(this.sp));
        this.sp.granularity.add(new JTextField(5));
        ((JTextField) this.sp.granularity.get(i)).setText("1");
        ScatterPlotEditorPanel.setPreferredAsMax((JTextField) this.sp.granularity.get(i));
        hBox4.add(new JLabel("Set granularity of slider: "));
        hBox4.add((JTextField) this.sp.granularity.get(i));

        ((JTextField) this.sp.granularity.get(i)).addFocusListener(new GranularityListener(this.sp, i));
        ((JTextField) this.sp.granularity.get(i)).addActionListener(new GranularityListener(this.sp, i));

        hBox4.add(Box.createHorizontalGlue());
        this.main.add(hBox4);

        double min, max;
        int varIndex = this.sp.dataSet.getColumn(((Node) this.sp.newCondBox.getSelectedItem()));
        min = max = this.sp.dataSet.getDouble(0, varIndex);

        for (int j = 0; j < this.sp.dataSet.getNumRows(); j++) {
            double temp = this.sp.dataSet.getDouble(j, varIndex);
            if (temp < min) min = temp;
            if (temp > max) max = temp;
        }

        this.sp.scrollers.add(new JScrollBar(Adjustable.HORIZONTAL, (int) FastMath.floor(min), 1, (int) FastMath.floor(min), (int) FastMath.ceil(max)));

        Box hBox10 = Box.createHorizontalBox();
        hBox10.add(Box.createHorizontalStrut(10));
        hBox10.add((JScrollBar) this.sp.scrollers.get(i));
        this.main.add(hBox10);

        ((JScrollBar) this.sp.scrollers.get(i)).addAdjustmentListener(new SliderListener(this.sp, i));

        Box hBox12 = Box.createHorizontalBox();
        hBox12.add(Box.createHorizontalStrut(10));
        hBox12.add(removeButton);
        this.main.add(hBox12);

        this.sp.slideLabels.add(new JLabel("Viewing Range: [" + ((JScrollBar) this.sp.scrollers.get(i)).getValue() + ", " + (((JScrollBar) this.sp.scrollers.get(i)).getValue() + ((JScrollBar) this.sp.scrollers.get(i)).getVisibleAmount()) + "]"));
        Box hBox11 = Box.createHorizontalBox();
        hBox11.add(Box.createHorizontalStrut(10));
        hBox11.add((JLabel) this.sp.slideLabels.get(i));
        this.main.add(hBox11);

        JComponent[] toRemove = new JComponent[4];
        toRemove[0] = hBox4;
        toRemove[1] = hBox10;
        toRemove[2] = hBox11;
        toRemove[3] = hBox12;
        removeButton.addActionListener(new RemovalListener(this.sp, this.main, toRemove, i));

        this.sp.redrawScatterPlot();

        this.main.revalidate();
        this.main.repaint();
    }
}

class RemovalListener implements ActionListener {
    private final JComponent container;
    private final JComponent[] contained;
    private final int index;
    private final ScatterPlotEditorPanel sp;

    public RemovalListener(ScatterPlotEditorPanel sp, JComponent container, JComponent[] contained, int index) {
        this.container = container;
        this.contained = contained;
        this.index = index;
        this.sp = sp;
    }

    public void actionPerformed(ActionEvent e) {
        this.sp.boxes.remove(this.index);
        this.sp.granularity.remove(this.index);
        this.sp.slideLabels.remove(this.index);
        this.sp.scrollers.remove(this.index);
        this.sp.condVariables.remove(this.index);
        this.sp.redrawScatterPlot();
        for (JComponent jComponent : this.contained) this.container.remove(jComponent);
        this.container.revalidate();
        this.container.repaint();
    }
}



