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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.List;
import java.util.Set;

/**
 * <p>Provides a sepset producer using conditional independence tests to generate
 * the Sepset map.</p>
 *
 * @author josephramsey
 * @see SepsetProducer
 * @see SepsetMap
 */
public class SepsetsSet implements SepsetProducer {
    private final SepsetMap sepsets;
    private final IndependenceTest test;
    private boolean verbose;
    private IndependenceResult result;

    public SepsetsSet(SepsetMap sepsets, IndependenceTest test) {
        this.sepsets = sepsets;
        this.test = test;
    }

    @Override
    public Set<Node> getSepset(Node a, Node b) {
        //isIndependent(a, b, sepsets.get(a, b));
        return this.sepsets.get(a, b);
    }

    @Override
    public boolean isUnshieldedCollider(Node i, Node j, Node k) {
        Set<Node> sepset = this.sepsets.get(i, k);
        if (sepset == null) throw new IllegalArgumentException("That triple was covered: " + i + " " + j + " " + k);
        else return !sepset.contains(j);
    }

    @Override
    public boolean isIndependent(Node a, Node b, Set<Node> c) {
        IndependenceResult result = this.test.checkIndependence(a, b, c);
        this.result = result;
        return result.isIndependent();
    }

    @Override
    public double getScore() {
        return -(this.result.getPValue() - this.test.getAlpha());
    }

    @Override
    public List<Node> getVariables() {
        return this.test.getVariables();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

}

