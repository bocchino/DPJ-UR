/**
 * Parallel nQueens backtracking
 * @author Robert L. Bocchino Jr.
 * October 2008
 */

import DPJRuntime.*;

public class Queens extends Harness {

    static int n;
    private static int seqCutoff = 1;
    private static int result = 0;


    public Queens(String[] args) {
	super("nQueens", args, 2, 3);
	if (args.length == 3)
	    seqCutoff = Integer.parseInt(args[2]);
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

    //reads Root because I have to check if col == n, and n resides in root
    static int place(int col, LL board) reads Root {
        if (col == n)
            return 0;
        int sum = 0;
        // Place a queen in all safe positions of column c,
        // then try placing a queen in the next column.
        // Parallel
        if (col < seqCutoff) {
            region C;
            IPArrayInt<C> c = new IPArrayInt<C>(n);
            foreach (int row in 0, n) {
                if (isSafe(board, row, col)) {
                    Tuple T = new Tuple(row, col);
                    // pass the new board onto the next col,
                    c[row] = place(col+1, new LL(T, board));
                }
            }
            // sum computation is sequential because nQueens computation gets
            // really slow at small n (n < 30). Computing this sum shouldn't
            // take long compared to the overall time of the computation.
            for (int i = 0; i < n; i++)
                sum += c[i];
        }
        // Sequential
        else {
            for (int row = 0; row < n; row++) {
                if (isSafe(board, row, col)) {
                    if (col == (n-1))
                        return 1;
                    else {
                        // pass the new board onto the next recursive call
                        Tuple T = new Tuple(row, col);
                        sum += place(col+1, new LL(T, board));
                    }
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
	result = place(0, null);
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
	Queens q = new Queens(args);
	q.run();
        // System.out.println("nQueens: n = " + n);
        // System.out.println("nQueens: solution = " + result);
        // System.out.println("nQueens done");
    }
}
