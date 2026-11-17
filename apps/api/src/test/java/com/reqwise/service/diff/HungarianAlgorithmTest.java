package com.reqwise.service.diff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HungarianAlgorithmTest {

    @Test
    @DisplayName("Solves optimal bipartite minimum cost assignment on square cost matrix")
    void shouldSolveSquareMatrixAssignment() {
        // 3x3 cost matrix:
        // [10, 19, 8]
        // [10,  5, 7]
        // [13, 12, 6]
        // Optimal assignment: row 0 -> col 0 (10), row 1 -> col 1 (5), row 2 -> col 2 (6) => cost = 21
        // Or row 0 -> col 2 (8), row 1 -> col 1 (5), row 2 -> col 0 (13) => cost = 26
        // Min cost: row 0 -> col 0 (10), row 1 -> col 1 (5), row 2 -> col 2 (6) = 21
        double[][] cost = {
                {10, 19, 8},
                {10, 5, 7},
                {13, 12, 6}
        };

        HungarianAlgorithm algo = new HungarianAlgorithm(cost);
        int[] matching = algo.execute();

        assertThat(matching).hasSize(3);
        double totalCost = 0;
        for (int r = 0; r < 3; r++) {
            totalCost += cost[r][matching[r]];
        }
        assertThat(totalCost).isEqualTo(21.0);
    }

    @Test
    @DisplayName("Solves optimal assignment on rectangular matrix (more columns than rows)")
    void shouldSolveRectangularMatrixAssignment() {
        double[][] cost = {
                {10, 2, 8},
                {4, 15, 7}
        };

        HungarianAlgorithm algo = new HungarianAlgorithm(cost);
        int[] matching = algo.execute();

        assertThat(matching).hasSize(2);
        // Row 0 should match col 1 (cost 2)
        // Row 1 should match col 0 (cost 4)
        assertThat(matching[0]).isEqualTo(1);
        assertThat(matching[1]).isEqualTo(0);
    }
}
