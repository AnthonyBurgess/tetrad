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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesImObs;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.NumberFormat;
import java.util.ArrayList;

/**
 * This is the JTable which displays the getModel parameter set (an Model).
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */

////////////////////////////////////////////////////////
// display and edit a JPD
////////////////////////////////////////////////////////

class BayesImNodeEditingTableObs extends JTable {
    private int focusRow;
    private int focusCol;
    private int lastX;
    private int lastY;

    /**
     * Constructs a new editing table from a given editing table model.
     */
    public BayesImNodeEditingTableObs(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        Model model = new Model(bayesIm, this);
        model.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    BayesImNodeEditingTableObs.this.firePropertyChange("modelChanged", null, null);
                }
            }
        });
        this.setModel(model);

        ////////////////////////////////////////////////////////////////

        this.setDefaultEditor(Number.class, new NumberCellEditor());
        this.setDefaultRenderer(Number.class, new NumberCellRenderer());
        this.getTableHeader().setReorderingAllowed(false);
        this.getTableHeader().setResizingAllowed(true);
        this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.setCellSelectionEnabled(true);

        ListSelectionModel rowSelectionModel = this.getSelectionModel();

        rowSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel m = (ListSelectionModel) (e.getSource());
                BayesImNodeEditingTableObs.this.setFocusRow(m.getAnchorSelectionIndex());
            }
        });

        ListSelectionModel columnSelectionModel = this.getColumnModel()
                .getSelectionModel();

        columnSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    public void valueChanged(ListSelectionEvent e) {
                        ListSelectionModel m =
                                (ListSelectionModel) (e.getSource());
                        BayesImNodeEditingTableObs.this.setFocusColumn();
                    }
                });

        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    BayesImNodeEditingTableObs.this.showPopup(e);
                }
            }
        });

        this.setFocusRow(0);
        this.setFocusColumn();
    }

    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        if (this.getModel() instanceof Model) {
            FontMetrics fontMetrics = this.getFontMetrics(this.getFont());
            Model model = (Model) this.getModel();

            for (int i = 0; i < model.getColumnCount(); i++) {
                TableColumn column = this.getColumnModel().getColumn(i);
                String columnName = model.getColumnName(i);
                int currentWidth = column.getPreferredWidth();

                if (columnName != null) {
                    int minimumWidth = fontMetrics.stringWidth(columnName) + 8;

                    if (minimumWidth > currentWidth) {
                        column.setPreferredWidth(minimumWidth);
                    }
                }
            }
        }
    }

    private void showPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem randomizeEntireTable =
                new JMenuItem("Randomize entire table");
        JMenuItem clearEntireTable = new JMenuItem("Clear entire table");

        randomizeEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                /*if (existsCompleteRow(bayesIm, nodeIndex)) {*/
                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will modify all values in the table. " +
                                "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }
                /*}*/

                BayesImNodeEditingTableObs editingTable =
                        BayesImNodeEditingTableObs.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                // randomize the jpd
                //getBayesIm().getJPD().createRandomCellTable();
                BayesImNodeEditingTableObs.this.getBayesIm().createRandomCellTable();

                BayesImNodeEditingTableObs.this.getEditingTableModel().fireTableDataChanged();
                BayesImNodeEditingTableObs.this.firePropertyChange("modelChanged", null, null);
            }
        });

        clearEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //if (existsCompleteRow(bayesIm, nodeIndex)) {
                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will delete all values in the table. " +
                                "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }
                //}

                BayesImNodeEditingTableObs editingTable =
                        BayesImNodeEditingTableObs.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                // clear the jpd
                BayesImNodeEditingTableObs.this.getBayesIm().getJPD().clearCellTable();

                BayesImNodeEditingTableObs.this.getEditingTableModel().fireTableDataChanged();
                BayesImNodeEditingTableObs.this.firePropertyChange("modelChanged", null, null);
            }
        });

        popup.add(randomizeEntireTable);
        popup.add(clearEntireTable);

        lastX = e.getX();
        lastY = e.getY();

        popup.show((Component) e.getSource(), e.getX(), e.getY());
    }

    public void setModel(TableModel model) {
        super.setModel(model);
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
    private void setFocusRow(int row) {
        if (row == -1) {
            return;
        }

        Model editingTableModel = (Model) this.getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        focusRow = row;

        if (focusRow < this.getRowCount()) {
            this.setRowSelectionInterval(focusRow, focusRow);
            this.editCellAt(focusRow, focusCol);
        }
    }

    /**
     * Sets the focus column to the anchor column currently being selected.
     */
    private void setFocusColumn() {
        Model editingTableModel = (Model) this.getModel();
        int failedCol = editingTableModel.getFailedCol();

        if (failedCol != -1) {
            int col = failedCol;
            editingTableModel.resetFailedCol();
        }

        // always focus on the last column of each row (the joint probability
        // of the combination of variable values denoted by the other columns
        // in that row)
        focusCol = this.getColumnCount() - 1;

        this.setColumnSelectionInterval(focusCol, focusCol);
        this.editCellAt(focusRow, focusCol);
    }

    private Model getEditingTableModel() {
        return (Model) this.getModel();
    }

    private MlBayesImObs getBayesIm() {
        return this.getEditingTableModel().getBayesIm();
    }

    private int getLastX() {
        return lastX;
    }

    private int getLastY() {
        return lastY;
    }


    //////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////


    //////////////////////////////////////////
    // The abstract table model of the jpd
    //////////////////////////////////////////

    static final class Model extends AbstractTableModel {

        /**
         * The BayesIm being edited.
         */
        private final MlBayesImObs bayesIm;

        /**
         * The messageAnchor that takes the user through the process of editing
         * the probability tables.
         */
        private final JComponent messageAnchor;

        private int failedRow = -1;
        private int failedCol = -1;
        private PropertyChangeSupport pcs;

        private final java.util.List<Node> obsNodes = new ArrayList<>();

        /////////////////////////////////////////////////////////////
        // construct a new editing table model for a given bayesIm
        //
        public Model(BayesIm bayesIm, JComponent messageAnchor) {
            if (bayesIm == null) {
                throw new NullPointerException("Bayes IM must not be null.");
            }

            if (messageAnchor == null) {
                throw new NullPointerException(
                        "Message anchor must not be null.");
            }

            // cast the bayesIm to MlBayesImObs
            this.bayesIm = (MlBayesImObs) bayesIm;

            this.messageAnchor = messageAnchor;

            // construct an arrayList of observed nodes

            // two equivalent ways
			/*
			Graph graph = bayesIm.getBayesPm().getDag();
			for (Object o : graph.getNodes()) {
				Node nodeO = (Node) o;
				if (nodeO.getNodeType() == NodeType.MEASURED)
				{
					obsNodes.add(nodeO);
				}
			}
			*/

            for (int i = 0; i < bayesIm.getNumNodes(); i++) {
                Node nodeO = bayesIm.getNode(i);
                if (nodeO.getNodeType() == NodeType.MEASURED) {
                    obsNodes.add(nodeO);
                }
            }

            // This does not work: it gives a different ordering of the nodes
			/*
			obsNodes = bayesIm.getMeasuredNodes();
			*/

        }

        /**
         * @return the name of the given column.
         */
        public String getColumnName(int col) {
            if (col < obsNodes.size()) {
                return obsNodes.get(col).getName();
            } else if (col == obsNodes.size()) {
                return "Probability";  // last column is the joint probability
            } else {
                return null;
            }
        }

        //////////////////////////////////////////////////////////////////*
        // number of rows in the table.
        public int getRowCount() {
            return this.getBayesIm().getNumRows();
        }

        //////////////////////////////////////////////////////////////////
        // number of columns in the table
        // (number of variables in the bayeIm plus one for the probability
        // value)
        public int getColumnCount() {
            return obsNodes.size() + 1;
        }

        ////////////////////////////////////////////////////////////////////
        // Returns the value of the table at the given row and column. 
        // The type of value returned depends on the column.
        // If there are n variables in the bayesIm, the first n columns
        // have String values, representing the combination of node values.
        // The last column is a Double representing the probability of
        // this combination of node values.
        //
        public Object getValueAt(int tableRow, int tableCol) {

            if (tableCol < obsNodes.size()) {
                int categoryIndex = this.getBayesIm().getRowValues(tableRow)[tableCol];
                BayesPm bayesPm = this.getBayesIm().getBayesPm();
                return bayesPm.getCategory(obsNodes.get(tableCol),
                        categoryIndex);
            } else if (tableCol == obsNodes.size()) {
                return this.getBayesIm().getProbability(tableRow);
            } else {
                return null;
            }
        }

        ////////////////////////////////////////////////////////////////////
        // Determine whether a cell is in the column range to allow for editing
        // (only the last column (representing the prob value) can be edited.)
        public boolean isCellEditable(int row, int col) {
            return (col >= obsNodes.size());
        }

        ////////////////////////////////////////////////////////////////////
        // Set the value of the cell at (row, col) to 'aValue'.
        // Perform some error checking to make sure the probabilities add up.
        //
        public void setValueAt(Object aValue, int row, int col) {
            if ("".equals(aValue) || aValue == null) {    // cell is cleared
                this.getBayesIm().setProbability(row, Double.NaN);
                this.fireTableRowsUpdated(row, row);
                this.getPcs().firePropertyChange("modelChanged", null, null);
                return;
            }

            try {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                double probability = Double.parseDouble((String) aValue);
//                probability = Double.parseDouble(nf.format(probability));
                double sum = this.sumProb(row) + probability;

                double oldProbability = this.getBayesIm().getProbability(row);

                if (!Double.isNaN(oldProbability)) { // there is an old value 
                    oldProbability = Double.parseDouble(nf.format(oldProbability));
                }

                if (probability == oldProbability) { // value is retained
                    return;
                }

                if (this.probabilityOutOfRange(probability)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Probabilities must be in range [0.0, 1.0].");
                    failedRow = row;
                    failedCol = col;
                } else if (sum > 1.00005) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Sum of probabilities in the column must not exceed 1.0.");
                    failedRow = row;
                    failedCol = col;
                } else {
                    this.getBayesIm().setProbability(row, probability);

                    // all the rows are filled in and the total is less than 1
                    if ((this.numNanRows() == 0) && (sum < 0.99995)) {
                        //if (sumInRow < 0.99995 || sumInRow > 1.00005) {
                        // emptyRow(row);   // too harsh

                        // only two rows in the table: filling in one row will
                        // cause the other row to be filled in
                        if (this.getBayesIm().getNumRows() == 2) {
                            this.getBayesIm().setProbability(row, probability);
                            this.fillInSingleRemainingRow();
                            this.fireTableRowsUpdated(row, row);
                            this.getPcs().firePropertyChange("modelChanged", null,
                                    null);
                        } else {
                            // set the probability back to before editing
                            this.getBayesIm().setProbability(row, oldProbability);
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Probabilities in the column must sum up to 1.0.\n"
                                            + "Leave one row (or two) blank while working.");
                            failedRow = row;
                            failedCol = col;
                        }

                    } else // things are ok
                    {
                        this.fillInSingleRemainingRow();
                        this.fillInZerosIfSumIsOne();
                        this.fireTableRowsUpdated(row, row);
                        this.getPcs().firePropertyChange("modelChanged", null,
                                null);
                    }
                }
            } catch (NumberFormatException e) {  // not a number
                e.printStackTrace();
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                failedRow = row;
                failedCol = col;
            }
        }

        public void addPropertyChangeListener(PropertyChangeListener l) {
            this.getPcs().addPropertyChangeListener(l);
        }

        private PropertyChangeSupport getPcs() {
            if (pcs == null) {
                pcs = new PropertyChangeSupport(this);
            }
            return pcs;
        }

        // fill in the last remaining row so that the rows sum up to 1
        private void fillInSingleRemainingRow() {
            int leftOverRow = this.uniqueNanRow();

            if (leftOverRow != -1) {
                double difference = 1.0 - this.sumProb(leftOverRow);
                this.getBayesIm().setProbability(leftOverRow, difference);
            }
        }

        // make all remaining rows 0 if the filled in rows already sum up to 1
        private void fillInZerosIfSumIsOne() {
            double sum = this.sumProb(-1);  // sum all the rows without skipping

            if (sum > 0.9995 && sum < 1.0005) {
                int numRows = this.getBayesIm().getNumRows();

                for (int i = 0; i < numRows; i++) {
                    double probability = this.getBayesIm().getProbability(i);

                    if (Double.isNaN(probability)) {
                        this.getBayesIm().setProbability(i, 0.0);
                    }
                }
            }
        }

        private boolean probabilityOutOfRange(double value) {
            return value < 0.0 || value > 1.0;
        }

        // one empty row only
        private int uniqueNanRow() {
            int numNanRows = 0;
            int lastNanRow = -1;

            for (int i = 0; i < this.getBayesIm().getNumRows(); i++) {
                double probability = this.getBayesIm().getProbability(i);
                if (Double.isNaN(probability)) {
                    numNanRows++;
                    lastNanRow = i;
                }
            }

            return numNanRows == 1 ? lastNanRow : -1;
        }

        // number of empty rows
        private int numNanRows() {
            int numNanRows = 0;

            for (int i = 0; i < this.getBayesIm().getNumRows(); i++) {
                double probability = this.getBayesIm().getProbability(i);
                if (Double.isNaN(probability)) {
                    numNanRows++;
                }
            }

            return numNanRows;
        }


        // sum of all probability entries in the table except the row rowToSkip
        // To sum every row, make rowToSkip an impossible value, e.g. -1
        private double sumProb(int rowToSkip) {
            double sum = 0.0;

            for (int i = 0; i < this.getBayesIm().getNumRows(); i++) {
                double probability = this.getBayesIm().getProbability(i);

                if (i != rowToSkip && !Double.isNaN(probability)) {

                    NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                    probability = Double.parseDouble(nf.format(probability));

                    sum += probability;
                }
            }

            return sum;
        }

        ///////////////////////////////////////
        // last column is a number, the others general objects
        // This is used to determine the formatting of the cell
        public Class getColumnClass(int col) {
            return col == this.getColumnCount() - 1 ? Number.class : Object.class;
        }

        // cast the bayesIm to MlBayesImObs
        public MlBayesImObs getBayesIm() {
            return bayesIm;
        }

        public JComponent getMessageAnchor() {
            return messageAnchor;
        }

        public int getFailedRow() {
            return failedRow;
        }

        public int getFailedCol() {
            return failedCol;
        }

        public void resetFailedRow() {
            failedRow = -1;
        }

        public void resetFailedCol() {
            failedCol = -1;
        }

    }
}






