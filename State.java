package solver;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents one Sokoban search node.
 *
 * A state captures the player's position, the positions of all crates, the
 * move sequence used to reach the state, and A* metadata such as `g`, `h`,
 * and `f`. The object is treated as immutable for search purposes: when the
 * solver explores a move, it creates a new State instead of mutating the old one.
 *
 * States are ordered by `f` so they can be stored in a priority queue.
 */
public class State implements Comparable<State> {

    public final int playerX; // X-coordinate of the player's current position
    public final int playerY; // Y-coordinate of the player's current position

    /**
     * Crate positions encoded as 1D tile IDs: `row * width + col`.
     * The array is kept sorted so crate lookups can use binary search and so
     * crate configurations can be compared consistently.
     */
    public final int[] crates;

    /**
     * Macro moves used to reach this state from its parent.
     * This is usually a short string made of walk steps followed by the final
     * push direction, for example `uurrl`.
     */
    public final String move;

    public final State parent; // Parent state, or `null` for the root

    public final int g; // Path cost from the start state to this state
    public final int h; // Heuristic estimate of the remaining cost to the goal
    public final int f; // Weighted A* score used for ordering states in the open list

    /**
     * Normalized player position used when comparing states.
     * The solver computes this value from the player's reachable region so
     * equivalent states can be recognized even if the player stands on a
     * different tile inside the same region.
     */
    public int normalizedPlayer;

    /**
     * Constructor for a new state. Used by the solver whenever states are
     * created during the solution process.
     *
     * @param playerX player column
     * @param playerY player row
     * @param crates sorted crate positions encoded as 1D tile IDs
     * @param g path cost from the start state
     * @param h heuristic estimate to the goal
     * @param parent parent state, or `null` for the root
     * @param move macro move taken from the parent to reach this state
     */
    public State(int playerX, int playerY, int[] crates, int g, int h, State parent, String move) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.crates = crates;
        this.move = move;
        this.parent = parent;

        this.g = g;
        this.h = h;
        this.f = g + (4 * h);

        // Default fallback before SokoBot overwrites this with the reachability-normalized value.
        this.normalizedPlayer = playerY * 1000 + playerX;
    }

    /**
     * Checks whether a crate occupies the given board cell.
     *
     * @param x         board column
     * @param y         board row
     * @param width     board width used to encode and decode tile IDs
     * @return          True if a crate is present at `(x, y)`
     */
    public boolean hasCrateAt(int x, int y, int width) {
        return Arrays.binarySearch(crates, y * width + x) >= 0;
    }

    /**
     *  Overridden method used by the priority queue to order states by `f`.
     *  States with lower `f` values are considered better. However, when 2 states
     *  have the same `f` value, the one with the lowest `g` value is chosen.
     */
    @Override
    public int compareTo(State other) {
        int cmp = Integer.compare(this.f, other.f);
        if (cmp == 0) {
            return Integer.compare(other.g, this.g);
        }
        return cmp;
    }

    /**
     * States are considered equal when they have the same crate layout and the
     * same normalized player region.
     *
     * This is stronger than comparing only the raw player coordinates, because
     * the solver may treat multiple player tiles as equivalent if they are all
     * reachable without moving any crates.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State)) return false;
        State other = (State) o;

        return this.normalizedPlayer == other.normalizedPlayer
                && Arrays.equals(this.crates, other.crates);
    }

    @Override
    public int hashCode() {
        return 31 * normalizedPlayer + Arrays.hashCode(crates);
    }

    /**
     * Reconstructs the full move sequence from the start state to this state.
     *
     * The method walks up the parent chain, collects each macro move, then
     * reverses the collected steps so they are returned in execution order.
     *
     * @return concatenated move sequence from root to this state
     */
    public String reconstructPath() {
        List<String> steps = new ArrayList<>();
        State current = this;

        // The root state has no move string, so stop when we reach it.
        while (current != null && current.move != null && !current.move.isEmpty()) {
            steps.add(current.move);
            current = current.parent;
        }

        // Parent traversal collects moves in reverse, so flip them before joining.
        Collections.reverse(steps);
        StringBuilder sb = new StringBuilder();
        for (String s : steps) {
            sb.append(s);
        }
        return sb.toString();
    }
}
