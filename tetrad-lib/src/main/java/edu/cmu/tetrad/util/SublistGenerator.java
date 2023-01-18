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

package edu.cmu.tetrad.util;

import org.apache.commons.math3.special.Gamma;

import static java.lang.Math.exp;
import static java.lang.Math.round;

/**
 * Generates (nonrecursively) all of the sublists of size b from a list of size a,
 * where a, b are nonnegative integers and a &gt;= b.  The values of a and b are given
 * in the constructor, and the sequence of sublists is obtained by repeatedly calling
 * the next() method.  When the sequence is finished, null is returned.
 * <p>
 * A valid combination for the sublists generated by this class is an array x[] of b
 * integers i, 0 &lt;= i &lt; a, such that x[j] &lt; x[j + 1] for each j from 0 to b - 1.
 * <p>
 * Works by calling ChoiceGenerator with increasingly larger values of a.
 * <p>
 * To see what this class does, try calling ChoiceGenerator.testPrint(5, 3), for
 * instance.
 *
 * @author Joseph Ramsey
 */
public final class SublistGenerator {

    /**
     * The number of objects being selected from.
     */
    private final int a;

    /**
     * The number of objects in the desired selection.
     */
    private int b;

    /**
     * The difference between a and b (should be nonnegative).
     */
    private int diff;

    /**
     * The internally stored choice.
     */
    private int[] choiceLocal;

    /**
     * The choice that is returned. Used, since the returned array can be
     * modified by the user.
     */
    private int[] choiceReturned;

    /**
     * Indicates whether the next() method has been called since the last
     * initialization.
     */
    private boolean begun;

    /**
     * Maximum a.
     */
    private final int depth;

    /**
     * Effective maximum a.
     */
    private int effectiveDepth;

    /**
     * Constructs a new generator for sublists for a list of size b taken a at a time.
     * Once this initialization has been performed, successive calls to next() will
     * produce the series of combinations.  To begin a new series at any time,
     * call this init method again with new values for a and b.
     *
     * @param a     the size of the list being selected from.
     * @param depth the maximum number of elements selected.
     */
    public SublistGenerator(int a, int depth) {
        if ((a < 0) || depth < -1) {
            throw new IllegalArgumentException();
        }

        this.a = a;
        this.b = 0;
        this.depth = depth;

        this.effectiveDepth = depth;
        if (depth == -1) this.effectiveDepth = a;
        if (depth > a) this.effectiveDepth = a;

        initialize();
    }

    public static int getNumCombinations(int a, int b) {
        int numCombinations = 0;

        for (int c = 0; c <= b; c++) {
            numCombinations += (int) round(exp(Gamma.logGamma(a + 1) - Gamma.logGamma(c + 1) - Gamma.logGamma((a - c) + 1)));
        }

        return numCombinations;
    }

    private void initialize() {
        this.choiceLocal = new int[this.b];
        this.choiceReturned = new int[this.b];
        this.diff = this.a - this.b;

        // Initialize the choice array with successive integers [0 1 2 ...].
        // Set the value at the last index one less than it would be in such
        // a series, ([0 1 2 ... b - 2]) so that on the first call to next()
        // the first combination ([0 1 2 ... b - 1]) is returned correctly.
        for (int i = 0; i < this.b - 1; i++) {
            this.choiceLocal[i] = i;
        }

        if (this.b > 0) {
            this.choiceLocal[this.b - 1] = this.b - 2;
        }

        this.begun = false;
    }

    /**
     * @return the next combination in the series, or null if the series is
     * finished.
     */
    public synchronized int[] next() {
//        if (getB() == this.choiceLocal.length) return null;

        int i = getB();

        // Scan from the right for the first index whose value is less than
        // its expected maximum (i + diff) and perform the fill() operation
        // at that index.
        while (--i > -1) {
            if (this.choiceLocal[i] < i + this.diff) {
                fill(i);
                this.begun = true;
                System.arraycopy(this.choiceLocal, 0, this.choiceReturned, 0, this.b);
                return this.choiceReturned;
            }
        }

        if (this.begun) {
            this.b++;

            if (this.b > this.effectiveDepth) {
                return null;
            }

            initialize();
            return next();
        } else {
            this.begun = true;
            System.arraycopy(this.choiceLocal, 0, this.choiceReturned, 0, this.b);
            return this.choiceReturned;
        }
    }

    /**
     * This static method will print the series of combinations for a choose depth
     * to System.out.
     *
     * @param a     the number of objects being selected from.
     * @param depth the number of objects in the desired selection.
     */
    @SuppressWarnings("SameParameterValue")
    public static void testPrint(int a, int depth) {
        SublistGenerator cg = new SublistGenerator(a, depth);
        int[] choice;

        System.out.println();
        System.out.println(
                "Printing combinations for " + a + " choose " + depth + ":");
        System.out.println();

        while ((choice = cg.next()) != null) {
            if (choice.length == 0) {
                System.out.println("zero-length array");
            } else {
                for (int aChoice : choice) {
                    System.out.print(aChoice + "\t");
                }

                System.out.println();
            }
        }

        System.out.println();
    }

    public String toString() {
        return "Depth choice generator: a = " + this.a + " depth = " + this.depth;
    }

    /**
     * @return Ibid.
     */
    @SuppressWarnings("UnusedDeclaration")
    public int getA() {
        return this.a;
    }

    /**
     * @return Ibid.
     */
    private int getB() {
        return this.b;
    }

    /**
     * Fills the 'choice' array, from index 'index' to the end of the array,
     * with successive integers starting with choice[index] + 1.
     *
     * @param index the index to begin this incrementing operation.
     */
    private void fill(int index) {
        this.choiceLocal[index]++;

        for (int i = index + 1; i < getB(); i++) {
            this.choiceLocal[i] = this.choiceLocal[i - 1] + 1;
        }
    }
}


