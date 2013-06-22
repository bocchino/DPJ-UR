/**
 * Parallel nQueens backtracking
 * @author Robert L. Bocchino Jr.
 * October 2008
 */

import DPJRuntime.*;

public class QueensSeq extends Harness {

    static int n;
    private static int result = 0;

    public QueensSeq(String[] args) {
	super("nQueens", args, 2, 2);
        n = Integer.parseInt(args[1]);
    }

    // wrapper for (row, col) tuples
    static class Tuple {
        public final int row;
        public final int col;

        //pure because fields are final
        public Tuple(int r, int c) pure { this.row = r; this.col = c; }

        public String str() { return "(" + this.row + "," + this.col + ")"; }
    }

    // custom linked list class that enables safe parallelism
    // I will add to the start of the list rather than the end
    static class LL {
        public final Tuple tuple;
        public final LL next;

        //pure because fields are final
        public LL(Tuple T, LL n) pure { this.tuple = T; this.next = n; }
    }

    //pure because I am only reading final fields
    static boolean isSafe(LL L, int r1, int c1) pure {
        while (L != null) {
            Tuple T2 = L.tuple;
            int delta =  c1 - T2.col;
            int r2 = T2.row;
            if (r1 == r2 || r1 == r2 + delta || r1 == r2 - delta)
                return false;
            L = L.next;
        }
        return true;
    }

    //Things I need to fix: isSafe (use row and col instead of tuple)
    //only allocate a tuple when adding to the board
    //check cutoff before array allocation (if seq, no alloc, else alloc)
    //reads Root because I have to check if col == n, and n resides in root
    static int placeSeq(int col, LL board) reads Root {
        if (col == n)
            return 0;
        // Place a queen in all safe positions of column c,
        // then try placing a queen in the next column.
        // Parallel
        int sum = 0;
        for (int row = 0; row < n; row++) {
            if (isSafe(board, row, col)) {
                // pass the new board onto the next recursive call
                if (col == (n-1))
                    return 1;
                else {
                    Tuple T = new Tuple(row, col);
                    sum += placeSeq(col+1, new LL(T, board));
                }
            }
        }
        return sum;
    }

    @Override
    public void initialize() {
        return;
    }

    @Override
    public void runTest() {
        return;
    }

    @Override
    public void runWork() {
	result = placeSeq(0, null);
    }

    @Override
    public void usage() {
	System.err.println("Usage:  java " + progName +
                           ".java [mode] [size] [seqsize]");
	System.err.println("mode = TEST, IDEAL, TIME");
	System.err.println("size = problem size (int)");
	System.err.println("seqsize = sequential problem size (int)");
    }

    public static void main(String[] args) {
	QueensSeq q = new QueensSeq(args);
	q.run();
        // System.out.println("nQueens: n = " + n);
        // System.out.println("nQueens: solution = " + result);
        // System.out.println("nQueens done");
    }
}
