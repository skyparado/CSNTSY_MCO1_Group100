package solver;

import java.util.HashSet;
import java.util.Set;
 
/**
 * Represents a single state of the Sokoban puzzle: where the player is,
 * and where all the crates are. Immutable - moving produces a new State.
 */
public class State {
 
    // Player position
    public final int playerX;
    public final int playerY;
 
    // Crate positions, stored as a set of packed coordinates (x * 1000 + y)
    // Using a set gives O(1) lookups for "is there a crate at (x,y)?"
    public final Set<Integer> crates;
 
    // The move taken to reach this state from its parent ('u','d','l','r'), or
    // '\0' for the root/start state.
    public final char move;
 
    // The parent state, used to reconstruct the solution path. Null for the
    // start state.
    public final State parent;
 
    public State(int playerX, int playerY, Set<Integer> crates, char move, State parent) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.crates = crates;
        this.move = move;
        this.parent = parent;
    }
 
    /** Helper to pack an (x, y) coordinate into a single int key for the set. */
    public static int pack(int x, int y) {
        return x * 1000 + y;
    }
 
    public boolean hasCrateAt(int x, int y) {
        return crates.contains(pack(x, y));
    }
 
    /**
     * Returns true if (x, y) is a wall in the map.
     * Bounds-checked so we never index out of range.
     */
    private boolean isWall(char[][] mapData, int width, int height, int x, int y) {
        if (x < 0 || y < 0 || y >= height || x >= width) {
            return true; // treat out-of-bounds as a wall
        }
        return mapData[y][x] == '#';
    }
 
    /**
     * Generates all states reachable from this one via a single player move,
     * respecting wall collisions and crate-pushing rules.
     *
     * Movement deltas:
     *   'u' -> (0, -1)
     *   'd' -> (0, +1)
     *   'l' -> (-1, 0)
     *   'r' -> (+1, 0)
     *
     * For each direction:
     *   - The target cell (where the player would move to) must not be a wall.
     *   - If the target cell has a crate, the cell beyond it (in the same
     *     direction) must be free (not a wall and not also occupied by another
     *     crate). If so, the crate is pushed and the player moves into the
     *     crate's old position.
     *   - If the target cell is empty (no wall, no crate), the player simply
     *     moves there.
     */
    public Set<State> getValidMoves(char[][] mapData, int width, int height) {
        Set<State> nextStates = new HashSet<>();
 
        char[] dirs = {'u', 'd', 'l', 'r'};
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
 
        for (int i = 0; i < dirs.length; i++) {
            int newX = playerX + dx[i];
            int newY = playerY + dy[i];
 
            // Can't move into a wall
            if (isWall(mapData, width, height, newX, newY)) {
                continue;
            }
 
            if (hasCrateAt(newX, newY)) {
                // Trying to push a crate: check the cell beyond it
                int beyondX = newX + dx[i];
                int beyondY = newY + dy[i];
 
                if (isWall(mapData, width, height, beyondX, beyondY)) {
                    continue; // crate would be pushed into a wall
                }
                if (hasCrateAt(beyondX, beyondY)) {
                    continue; // crate would be pushed into another crate
                }
 
                // Valid push: move the crate from (newX, newY) to (beyondX, beyondY)
                Set<Integer> newCrates = new HashSet<>(crates);
                newCrates.remove(pack(newX, newY));
                newCrates.add(pack(beyondX, beyondY));
 
                nextStates.add(new State(newX, newY, newCrates, dirs[i], this));
            } else {
                // Simple move, no crate involved
                nextStates.add(new State(newX, newY, crates, dirs[i], this));
            }
        }
 
        return nextStates;
    }
 
    /**
     * Reconstructs the move sequence (e.g. "ulldr") from the start state to
     * this state by walking up the parent chain and reversing it.
     */
    public String reconstructPath() {
        StringBuilder sb = new StringBuilder();
        State current = this;
        while (current.parent != null) {
            sb.append(current.move);
            current = current.parent;
        }
        return sb.reverse().toString();
    }
 
    /**
     * Checks whether all crates are on target cells. mapData should contain
     * '.' for target locations.
     */
    public boolean isGoal(char[][] mapData) {
        for (int packed : crates) {
            int x = packed / 1000;
            int y = packed % 1000;
            if (mapData[y][x] != '.') {
                return false;
            }
        }
        return true;
    }
 
    /**
     * Equality and hashCode are based on player position + crate positions,
     * since that fully determines a Sokoban state. This lets HashSet-based
     * visited/closed sets (used by BFS/A*) correctly detect duplicate states.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof State)) return false;
        State other = (State) o;
        return playerX == other.playerX
                && playerY == other.playerY
                && crates.equals(other.crates);
    }
 
    @Override
    public int hashCode() {
        int result = 31 * playerX + playerY;
        result = 31 * result + crates.hashCode();
        return result;
    }
}