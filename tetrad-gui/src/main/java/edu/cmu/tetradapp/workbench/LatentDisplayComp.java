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

package edu.cmu.tetradapp.workbench;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * Eliptical variable display for a latent.
 */
public class LatentDisplayComp extends JComponent implements DisplayComp {
    private boolean selected;

    public LatentDisplayComp(String name) {
        this.setBackground(DisplayNodeUtils.getNodeFillColor());
        this.setFont(DisplayNodeUtils.getFont());
        this.setName(name);
        setSize(this.getPreferredSize());
    }

    public void setName(String name) {
        super.setName(name);
        this.setSize(this.getPreferredSize());
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean contains(int x, int y) {
        return this.getShape().contains(x, y);
    }

    /**
     * @return the shape of the component.
     */
    private Shape getShape() {
        return new Ellipse2D.Double(0, 0, this.getPreferredSize().width - 1,
                this.getPreferredSize().height - 1);
    }

    /**
     * Paints the component.
     *
     * @param g the graphics context.
     */
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        FontMetrics fm = this.getFontMetrics(DisplayNodeUtils.getFont());
        int width = this.getPreferredSize().width;
        int stringWidth = fm.stringWidth(this.getName());
        int stringX = (width - stringWidth) / 2;
        int stringY = fm.getAscent() + DisplayNodeUtils.getPixelGap();

        g2.setColor(this.isSelected() ? DisplayNodeUtils.getNodeSelectedFillColor() :
                DisplayNodeUtils.getNodeFillColor());
        g2.fill(this.getShape());
        g2.setColor(this.isSelected() ? DisplayNodeUtils.getNodeSelectedEdgeColor() :
                DisplayNodeUtils.getNodeEdgeColor());
        g2.draw(this.getShape());
        g2.setColor(DisplayNodeUtils.getNodeTextColor());
        g2.setFont(DisplayNodeUtils.getFont());
        g2.drawString(this.getName(), stringX, stringY);
    }

    /**
     * Calculates the size of the component based on its name.
     */
    public Dimension getPreferredSize() {
        FontMetrics fm = this.getFontMetrics(DisplayNodeUtils.getFont());
        String name1 = this.getName();
        int textWidth = fm.stringWidth(name1);
        int textHeight = fm.getAscent();
        int width = textWidth + fm.getMaxAdvance() + 5;
        int height = 2 * DisplayNodeUtils.getPixelGap() + textHeight + 5;

        width = (width < 60) ? 60 : width;

        return new Dimension(width, height);
    }

    private boolean isSelected() {
        return selected;
    }
}




