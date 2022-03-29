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

import edu.cmu.tetrad.search.FindTwoFactorClusters.Algorithm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.DoubleTextField.Filter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Edits the properties of a measurement FtfcParams. See
 * BasicIndTestParamsEditor for more explanations.
 *
 * @author Joseph Ramsey
 */
class FtfcIndTestParamsEditor extends JComponent {
    private final Parameters FtfcParams;

    public FtfcIndTestParamsEditor(Parameters paramsPureClusters) {
        FtfcParams = paramsPureClusters;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");
        DoubleTextField alphaField = new DoubleTextField(this.getParams().getDouble("alpha", 0.001), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);

        alphaField.setFilter(new Filter() {
            public double filter(double value, double oldValue) {
                try {
                    FtfcIndTestParamsEditor.this.getParams().set("alpha", 0.001);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        JComboBox<Algorithm> algorithmSelector = new JComboBox<>();
        algorithmSelector.addItem(Algorithm.SAG);
        algorithmSelector.addItem(Algorithm.GAP);

        Algorithm algorithmType = (Algorithm) this.getParams().get("ftfcAlgorithm", Algorithm.GAP);
        algorithmSelector.setSelectedItem(algorithmType);

        algorithmSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Algorithm index = (Algorithm) algorithmSelector.getSelectedItem();
                if (index != null) {
                    FtfcIndTestParamsEditor.this.getParams().set("ftfcAlgorithm", index);
                }
            }
        });

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Algorithm:"));
        b2.add(Box.createHorizontalGlue());
        b2.add(algorithmSelector);
        this.add(b2);

        this.add(Box.createHorizontalGlue());
        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Alpha:"));
        b3.add(Box.createHorizontalGlue());
        b3.add(alphaField);
        this.add(b3);
        this.add(Box.createHorizontalGlue());
    }

    private Parameters getParams() {
        return FtfcParams;
    }


}





