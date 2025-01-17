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

import edu.cmu.tetrad.calculator.expression.Equation;
import edu.cmu.tetrad.calculator.expression.ExpressionSignature;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.NamingProtocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;

/**
 * An editor for expressions.
 *
 * @author Tyler Gibson
 */
class ExpressionEditor extends JPanel {


    /**
     * The variable field.
     */
    private final JTextField variable;


    /**
     * The expression field.
     */
    private final JTextField expression;


    /**
     * Parser.
     */
    private final ExpressionParser parser;


    /**
     * The last field to have focus.
     */
    private JTextField lastFocused;


    /**
     * Focus listeners.
     */
    private final List<FocusListener> listeners = new LinkedList<>();


    /**
     * States whether the remove box is clicked.
     */
    private boolean remove;


    /**
     * The active selections if there is one.
     */
    private final List<Selection> selections = new LinkedList<>();


    /**
     * Normal selections color.
     */
    private static final Color SELECTION = new Color(204, 204, 255);
    private final PositionsFocusListener positionsListener;


    /**
     * Creates the editor given the data set being worked on.
     */
    public ExpressionEditor(DataSet data, String lhs, String rhs) {
        this.parser = new ExpressionParser(data.getVariableNames(), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);

        this.variable = new JTextField(5);
        this.variable.setText(lhs);
        this.expression = new JTextField(25);
        this.expression.setText(rhs);

        this.variable.addFocusListener(new VariableFocusListener(this.variable));
        this.expression.addFocusListener(new ExpressionFocusListener(this.expression));

        this.positionsListener = new PositionsFocusListener();
        this.expression.addFocusListener(this.positionsListener);

        Box box = Box.createHorizontalBox();
        box.add(this.variable);
        box.add(Box.createHorizontalStrut(5));
        box.add(new JLabel("="));
        box.add(Box.createHorizontalStrut(5));
        box.add(this.expression);
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(e -> {
            JCheckBox b = (JCheckBox) e.getSource();
            ExpressionEditor.this.remove = b.isSelected();
        });
        box.add(Box.createHorizontalStrut(2));
        box.add(checkBox);
        box.add(Box.createHorizontalGlue());

        add(box);


    }

    //============================ Public Method ======================================//

    /**
     * @return the expression.
     * @throws java.text.ParseException - If the values in the editor are not well-formed.
     */
    public Equation getEquation() throws ParseException {
        if (!NamingProtocol.isLegalName(this.variable.getText())) {
            this.variable.setSelectionColor(Color.RED);
            this.variable.select(0, this.variable.getText().length());
            this.variable.grabFocus();
            throw new ParseException(NamingProtocol.getProtocolDescription(), 1);
        }
        String equation = this.variable.getText() + "=" + this.expression.getText();
        try {
            return this.parser.parseEquation(equation);
        } catch (ParseException ex) {
            this.expression.setSelectionColor(Color.RED);
            this.expression.select(ex.getErrorOffset() - 1, this.expression.getText().length());
            this.expression.grabFocus();
            throw ex;
        }
    }

    /**
     * Adds a focus listener that will be notified about the focus events of
     * the fields in the editor.  The listener will only be notified of gain focus
     * events.
     */
    public void addFieldFocusListener(FocusListener listener) {
        this.listeners.add(listener);
    }


    /**
     * Sets the given variable in the variable field.
     *
     * @param var    - The variable to set.
     * @param append - States whether it should append to the field's getModel value or not.
     */
    private void setVariable(String var, boolean append) {
        if (append) {
            this.variable.setText(this.variable.getText() + var);
        } else {
            this.variable.setText(var);
        }
    }


    /**
     * Sets the given expression fragment in the expression field.
     *
     * @param exp    - Expression value to set.
     * @param append States whether it should append to the field's getModel value or not.
     */
    private void setExpression(String exp, boolean append) {
        if (exp == null) {
            return;
        }
        if (!this.selections.isEmpty()) {
            this.expression.grabFocus();

            int start = this.positionsListener.start;
            int end = this.positionsListener.end;

            if (start < end) {
//                expression.select(start, end);
                this.selections.add(new Selection(start, end));
                this.expression.setCaretPosition(this.positionsListener.caretPosition);
            }

            String text = this.expression.getText();
            Selection selection = this.selections.remove(0);

            if (caretInSelection(selection)) {
                this.expression.setText(text.substring(0, selection.x) + exp + text.substring(selection.y));
                this.adjustSelections(selection, exp);
                this.highlightNextSelection();

                this.positionsListener.start = this.expression.getSelectionStart();
                this.positionsListener.end = this.expression.getSelectionEnd();
                this.positionsListener.caretPosition = this.expression.getCaretPosition();

                return;
            }
        }

        if (append) {
            this.expression.setText(this.expression.getText() + exp);

        } else {
            this.expression.setText(exp);

        }
        this.positionsListener.start = 0;
        this.positionsListener.end = 0;
        this.positionsListener.caretPosition = 0;
    }


    /**
     * Adds the signature to the expression field.
     */
    public void addExpressionSignature(ExpressionSignature signature) {
        this.expression.grabFocus();

        int start = this.positionsListener.start;
        int end = this.positionsListener.end;
        int caret = this.positionsListener.caretPosition;

        if (start < end) {
//            expression.select(start, end);
            this.selections.add(new Selection(start, end));
//            expression.setCaretPosition(positionsListener.caretPosition);
        }

        String sig = signature.getSignature();
        String text = this.expression.getText();
        Selection selection = this.selections.isEmpty() ? null : this.selections.remove(0);
        // if empty add the sig with any selections.
        if (selection == null || !caretInSelection(selection)) {
            String newText = text.substring(0, caret) + signature.getSignature()
                    + text.substring(caret);


//            String newText = text + signature.getSignature();
            this.expression.setText(newText);
            addSelections(signature, newText, false);
            this.highlightNextSelection();
            return;
        }
        // otherwise there is a selections so we want to insert this sig in it.
        String replacedText = text.substring(0, selection.x) + sig + text.substring(selection.y);
        this.expression.setText(replacedText);
        this.adjustSelections(selection, sig);
        addSelections(signature, replacedText, true);
        this.highlightNextSelection();

        this.positionsListener.start = 0;
        this.positionsListener.end = 0;
        this.positionsListener.caretPosition = 0;
    }


    /**
     * Inserts the given symbol into the last focused field, of if there isn't one
     * the expression field.
     *
     * @param append States whether it should append to the field's getModel value or not.
     */
    public void insertLastFocused(String symbol, boolean append) {
        if (this.variable == this.lastFocused) {
            setVariable(symbol, append);
        } else {
            setExpression(symbol, append);
        }
    }


    public boolean removeSelected() {
        return this.remove;
    }

    //========================== Private Methods ====================================//


    /**
     * States whether the caret is in the getModel selection, if not false is returned and
     * all the selections are removed (as the user moved the caret around).
     */
    private boolean caretInSelection(Selection sel) {
        int caret = this.expression.getCaretPosition();
        if (caret < sel.x || sel.y < caret) {
            this.selections.clear();
            return false;
        }
        return true;
    }


    /**
     * Adds the selections for the given signature in the given text.
     */
    private void addSelections(ExpressionSignature signature, String newText, boolean addFirst) {
        int offset = 0;
        for (int i = 0; i < signature.getNumberOfArguments(); i++) {
            String arg = signature.getArgument(i);
            int index = newText.indexOf(arg);
            int end = index + arg.length();
            if (0 <= index) {
                if (addFirst) {
                    this.selections.add(i, new Selection(offset + index, offset + end));
                } else {
                    this.selections.add(new Selection(offset + index, offset + end));
                }
            }
            offset = offset + end;
            newText = newText.substring(end);
        }
    }


    private void fireGainedFocus() {
        FocusEvent evt = new FocusEvent(this, FocusEvent.FOCUS_GAINED);
        for (FocusListener l : this.listeners) {
            l.focusGained(evt);
        }
    }


    /**
     * Adjusts any getModel selections to the fact that the given selections was just
     * replaced by the given string.
     */
    private void adjustSelections(Selection selection, String inserted) {
        int dif = (selection.y - selection.x) - inserted.length();
        for (Selection sel : this.selections) {
            sel.x = sel.x - dif;
            sel.y = sel.y - dif;
        }
    }


    /**
     * Highlights the next selection.
     */
    private void highlightNextSelection() {
        System.out.println("Highlighting next selection.");

        if (!this.selections.isEmpty()) {
            Selection sel = this.selections.get(0);
            this.expression.setSelectionColor(ExpressionEditor.SELECTION);
            this.expression.select(sel.x, sel.y);
            this.expression.grabFocus();
        }
    }

    //========================== Inner class ==============================//


    /**
     * Represents a 1D line.
     */
    private static class Selection {
        private int x;
        private int y;

        public Selection(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }


    /**
     * Focus listener for the variable field.
     */
    private class VariableFocusListener implements FocusListener {

        private final JTextField field;

        public VariableFocusListener(JTextField field) {
            this.field = field;
        }

        public void focusGained(FocusEvent e) {
            ExpressionEditor.this.lastFocused = this.field;
            fireGainedFocus();
        }

        public void focusLost(FocusEvent e) {
        }
    }


    /**
     * Focus listener for the expression field.
     */
    private class ExpressionFocusListener implements FocusListener {

        private final JTextField field;

        public ExpressionFocusListener(JTextField field) {
            this.field = field;
        }

        public void focusGained(FocusEvent e) {
            ExpressionEditor.this.lastFocused = this.field;
            fireGainedFocus();
        }

        @Override
        public void focusLost(FocusEvent e) {

        }
    }

    private static class PositionsFocusListener extends FocusAdapter {
        private int start;
        private int end;
        private int caretPosition;

        public void focusLost(FocusEvent e) {
            JTextField textField = (JTextField) e.getSource();
            this.start = textField.getSelectionStart();
            this.end = textField.getSelectionEnd();
            this.caretPosition = textField.getCaretPosition();
        }
    }

}




