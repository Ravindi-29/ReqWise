package com.reqwise.service.diff;

import java.util.Arrays;

/**
 * Kuhn-Munkres (Hungarian) Algorithm for solving the optimal bipartite assignment problem.
 * Finds minimum cost matching on an N x M cost matrix in O(K^3) time, where K = max(N, M).
 */
public class HungarianAlgorithm {

    private final double[][] costMatrix;
    private final int rows;
    private final int cols;
    private final int dim;

    public HungarianAlgorithm(double[][] matrix) {
        this.rows = matrix.length;
        this.cols = matrix.length > 0 ? matrix[0].length : 0;
        this.dim = Math.max(rows, cols);
        this.costMatrix = new double[dim][dim];

        // Find max element for dummy padding
        double maxVal = 0.0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] > maxVal) {
                    maxVal = matrix[i][j];
                }
            }
        }
        double padVal = maxVal + 10.0;

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                if (i < rows && j < cols) {
                    this.costMatrix[i][j] = matrix[i][j];
                } else {
                    this.costMatrix[i][j] = padVal;
                }
            }
        }
    }

    /**
     * Executes the Kuhn-Munkres algorithm.
     * @return an int array of length 'rows', where result[i] is the matched column index (0 <= j < cols),
     *         or -1 if unmatched or matched to a dummy column.
     */
    public int[] execute() {
        if (rows == 0 || cols == 0) {
            int[] empty = new int[rows];
            Arrays.fill(empty, -1);
            return empty;
        }

        double[] u = new double[dim + 1];
        double[] v = new double[dim + 1];
        int[] p = new int[dim + 1];
        int[] way = new int[dim + 1];

        for (int i = 1; i <= dim; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[dim + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[dim + 1];

            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;

                for (int j = 1; j <= dim; j++) {
                    if (!used[j]) {
                        double cur = costMatrix[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }

                for (int j = 0; j <= dim; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }

                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        int[] result = new int[rows];
        Arrays.fill(result, -1);

        for (int j = 1; j <= dim; j++) {
            if (p[j] > 0 && p[j] <= rows) {
                int originalRow = p[j] - 1;
                int originalCol = j - 1;
                if (originalCol < cols) {
                    result[originalRow] = originalCol;
                }
            }
        }

        return result;
    }
}
