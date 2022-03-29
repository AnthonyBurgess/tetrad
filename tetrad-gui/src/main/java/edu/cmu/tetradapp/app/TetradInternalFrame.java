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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.ImageUtils;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Extends JInternalFrame to ask the user if she wants to close the window.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TetradInternalFrame extends JInternalFrame {

    /**
     *
     */
    private static final long serialVersionUID = 907395289049591825L;

    /**
     * Constructs a new frame which will throw up a warning dialog if someone
     * tries to close it.
     *
     * @param title the title of the frame.
     */
    public TetradInternalFrame(String title) {
        super(title, false, true, false, false);
        Image image = ImageUtils.getImage(this, "tyler16.png");
        this.setFrameIcon(new ImageIcon(image));

        setDefaultCloseOperation(JInternalFrame.DO_NOTHING_ON_CLOSE);
        this.addInternalFrameListener(new InternalFrameAdapter() {

            /**
             * Throws up a warning dialog and then closes the frame if the user
             * says to.  Otherwise ignores the attempt.
             */
            public void internalFrameClosing(InternalFrameEvent e) {
                ActionEvent e2 = new ActionEvent(e.getSource(),
                        ActionEvent.ACTION_PERFORMED, "FrameClosing");

                CloseSessionAction closeSessionAction =
                        new CloseSessionAction();
                closeSessionAction.actionPerformed(e2);
            }
        });
    }
}





