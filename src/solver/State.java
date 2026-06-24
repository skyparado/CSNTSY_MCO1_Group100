package solver;

// MODIFIED: Replaced Set and HashSet imports with Arrays, ArrayList, Collections, and List for primitive arrays and string processing.
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single state of the Sokoban puzzle: where the player is,
 * and where all the crates are. Immutable - moving produces a new State.
 * * // MODIFIED: Added `implements Comparable<State>` so it can be sorted in SokoBot's A* PriorityQueue.
 */
public class State implements Comparable<State> {

    // Player position
    public final int playerX;
    public final int playerY;

    // Crate positions, stored as a set of packed coordinates (x * 1000 + y)
    // Using a set gives O(1) lookups for "is there a crate at (x,y)?"
    // MODIFIED: Changed from Set<Integer> to a primitive int[] array for better memory performance.
    // It now uses 1D representation (row * width + col) and O(log N) binary search instead of an object Set.
    public final int[] crates;

    // The move taken to reach this state from its parent ('u','d','l','r'), or
    // '\0' for the root/start state.
    // MODIFIED: Changed from 'char' to 'String' to hold SokoBot's multi-step macro-moves.
    public final String move;

    // The parent state, used to reconstruct the solution path. Null for the
    // start state.
    public final State parent;

    // MODIFIED: Added A* Search Extensions previously found in SokoBot's inner classes.
    public final int g;            // Actual cost from start to this state
    public final int h;            // Heuristic estimate to goal
    public final int f;            // Total estimated cost (g + 4*h)

    // MODIFIED: Added normalizedPlayer for advanced equivalence checking in SokoBot's Closed Map.
    public int normalizedPlayer;

    // MODIFIED: Constructor signature updated to handle the primitive array, A* weights, and String macro moves.
    public State(int playerX, int playerY, int[] crates, int g, int h, State parent, String move) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.crates = crates;
        this.move = move;
        this.parent = parent;

        // MODIFIED: Initialize A* tracking variables.
        this.g = g;
        this.h = h;
        this.f = g + (4 * h); // WEIGHTED A*: Matches SokoBot's original heuristic weight

        // Default fallback for normalization
        this.normalizedPlayer = playerY * 1000 + playerX;
    }

    // MODIFIED: Removed the static 'pack' method because SokoBot inherently uses 1D coordinate conversions natively (y * width + x).

    // MODIFIED: Added this helper to replace Set lookups with high-speed binary search on the primitive array.
    public boolean hasCrateAt(int x, int y, int width) {
        return Arrays.binarySearch(crates, y * width + x) >= 0;
    }

    // MODIFIED: Added compareTo method to fulfill the Comparable interface required by SokoBot's PriorityQueue.
    @Override
    public int compareTo(State other) {
        int cmp = Integer.compare(this.f, other.f);
        if (cmp == 0) {
            return Integer.compare(other.g, this.g);
        }
        return cmp;
    }

    // MODIFIED: Removed isGoal(char[][] mapData) method entirely.
    // SokoBot.java checks the goal state much faster by directly comparing the int[] crates array to an int[] targets array using Arrays.equals().

    /**
     * Equality and hashCode are based on player position + crate positions,
     * since that fully determines a Sokoban state. This lets HashSet-based
     * visited/closed sets (used by BFS/A*) correctly detect duplicate states.
     * // MODIFIED: Overhauled equals() and hashCode() to use SokoBot's `normalizedPlayer`
     *              instead of exact `playerX`/`playerY`. Also updated to compare primitive arrays via Arrays.equals().
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State)) return false;
        State other = (State) o;

        // MODIFIED: We use normalizedPlayer instead of strict x/y, because if a player
        // can walk freely to another tile without pushing a box, the states are functionally identical.
        return this.normalizedPlayer == other.normalizedPlayer
                && Arrays.equals(this.crates, other.crates);
    }

    @Override
    public int hashCode() {
        // MODIFIED: Re-written to hash primitive arrays and the normalized player id.
        return 31 * normalizedPlayer + Arrays.hashCode(crates);
    }

    /**
     * Traverses the parent chain and reversing it.
     * MODIFIED: Updated from char appending to String appending to handle SokoBot's macro moves.
     */
    public String reconstructPath() {
        List<String> steps = new ArrayList<>();
        State current = this;

        // MODIFIED: Safe null/empty checks added for the macro-move string at the root.
        while (current != null && current.move != null && !current.move.isEmpty()) {
            steps.add(current.move);
            current = current.parent;
        }

        // MODIFIED: Replaced StringBuilder reverse with Collections.reverse() since we are reversing
        // whole string blocks (macro-moves) rather than individual characters.
        Collections.reverse(steps);
        StringBuilder sb = new StringBuilder();
        for (String s : steps) {
            sb.append(s);
        }
        return sb.toString();
    }
}