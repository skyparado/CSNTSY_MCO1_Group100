package solver;

import java.util.*;

public class SokoBot {
    
    /**
     * A helper class used to compute and store the reachability information of the player from a given position, considering the current crate configuration.
     * * This class contains a 2D boolean array 'reachable' that indicates which cells the player can reach,
     * as well as parent pointers to reconstruct paths and a normalized player position for hashing purposes.
     */
    private static class Reachability {

        int startX, startY;             // The starting coordinates of the player for the reachability computation.
        final boolean[][] reachable;    // A 2D array indicating whether each cell is reachable by the player given the current crate configuration and map layout.
        final int[][] parentX, parentY; // 2D arrays storing the parent coordinates for each reachable cell, used to reconstruct the path taken to reach that cell.
        final char[][] parentMove;      // A 2D array storing the move direction taken to reach each cell from its parent, used for path reconstruction.
        int normalizedPlayer;           // A normalized representation of the player's position, used for hashing in the closed set.

        // The constructor initializes the Reachability object.
        Reachability(int width, int height) {
            this.reachable = new boolean[height][width];
            this.parentX = new int[height][width];
            this.parentY = new int[height][width];
            this.parentMove = new char[height][width];
        }
    }

    /**
     * The main method that solves the Sokoban puzzle. It takes the width and height of the board, the map data representing walls and targets,
     * and the item data representing the initial positions of crates and the player.
     * @param width         The width of the Sokoban board.
     * @param height        The height of the Sokoban board.
     * @param mapData       A 2D array of characters representing the static elements of the board, such as walls ('#') and targets ('.').
     * @param itemsData     A 2D array of characters representing the dynamic elements of the board, such as the player's position ('@' or '+') and crate positions ('$' or '*').
     * @return              A string of characters containing the solution. Returns an empty string if no solution is found.
     */
    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {

        // Step 1: Parse the input to identify target positions, initial crate positions, and the player's starting position.

        List<Integer> targetList = new ArrayList<>();   // A list to store the 1D positions of target cells on the board.
        List<Integer> startcrateList = new ArrayList<>(); // A list to store the 1D positions of the crates at the start of the puzzle.

        // Variables to store the starting position of the player. Initialized to -1 to indicate that they have not been set yet.
        int startPRow = -1, startPCol = -1;

        // Scan the map and record where targets, crates, and the player are located.
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {

                int posId = r * width + c;  // Convert 2D coordinates to a single integer for easier storage and comparison.
                if (mapData[r][c] == '.') targetList.add(posId);

                if (itemsData[r][c] == '@' || itemsData[r][c] == '+') {
                    startPRow = r;
                    startPCol = c;
                } else if (itemsData[r][c] == '$' || itemsData[r][c] == '*') {
                    startcrateList.add(posId);
                }
            }
        }

        // Convert the lists of targets and crates to arrays and sort them for consistent ordering, which is crucial for hashing and comparisons.
        int[] targets = targetList.stream().mapToInt(i -> i).toArray();
        Arrays.sort(targets);

        int[] startcrates = startcrateList.stream().mapToInt(i -> i).toArray();
        Arrays.sort(startcrates);

        // If the initial configuration already matches the target configuration, return an empty string as no moves are needed.
        if (Arrays.equals(startcrates, targets)) return "";

        // Step 2: Precompute deadlock positions to quickly identify states that cannot lead to a solution, thus pruning the search space effectively.
        boolean[][] deadlockGrid = precomputeDeadlocks(width, height, mapData, targetList);

        // Generate target-specific pull distance grids
        int[][][] targetDistGrids = computeTargetDistGrids(width, height, mapData, targets);

        // Step 3: Initialize the A* search algorithm with the starting state, including the heuristic calculation for the initial configuration.
        int initialH = calculateHeuristic(startcrates, targetDistGrids, width, targets);
        if (initialH == Integer.MAX_VALUE) return "";

        // Create the initial State representing the starting state of the puzzle, with the player's position, crate positions, and heuristic cost.
        State startState = new State(startPCol, startPRow, startcrates, 0, initialH, null, "");

        // Initialize the open list (priority queue) for A* search and the closed set (hash map) to track visited states.
        // The open list will prioritize states based on their estimated total cost 'f'.
        PriorityQueue<State> openList = new PriorityQueue<>();
        Map<State, Integer> closed = new HashMap<>();
        openList.add(startState);

        // Direction vectors and corresponding move characters for navigating the board.
        // These will be used to generate new states based on player movements and crate pushes.
        int[] rowDirVectors = {-1, 1, 0, 0};
        int[] colDirVectors = {0, 0, -1, 1};
        char[] dirs = {'u', 'd', 'l', 'r'};

        // Set a time limit for the search to prevent excessive computation time.
        // This is a safeguard to ensure that the solver returns a result within a reasonable timeframe, even for complex puzzles.
        long startTime = System.currentTimeMillis();
        long TIME_LIMIT_MS = 14500;
        int iterations = 0;

        // Reusable structures to eradicate allocation overhead in the core loop
        Reachability reach = new Reachability(width, height);
        int[] reachQueue = new int[width * height];

        // Step 4: Main A* search loop. Continues until the open list is empty or a solution is found.
        while (!openList.isEmpty()) {
            // Poll the state with the lowest estimated total cost 'f' from the open list to explore next.
            State current = openList.poll();

            // Check if the time limit has been exceeded, if so, return an empty string to indicate that no solution was found within the time constraints.
            if (++iterations % 1000 == 0 && (System.currentTimeMillis() - startTime > TIME_LIMIT_MS)) {
                return "";
            }

            // If the current configuration of crates matches the target configuration, reconstruct and return the path of moves that led to this solution.
            if (Arrays.equals(current.crates, targets)) {
                return current.reconstructPath();
            }

            /* Compute the reachability of the player from the current position, given the current crate configuration.
                This is essential for determining which moves are possible from this state. */
            computeReachability(reach, reachQueue, current.playerY, current.playerX, current.crates, mapData, width, height);

            /*  Apply normalizedPlayer to the current State which internally handles hashing.
                This helps to avoid re-exploring states that have already been visited at a lower or equal cost. */
            current.normalizedPlayer = reach.normalizedPlayer;

            // Check if this state has been visited before with a lower or equal cost. If so, skip processing this state.
            Integer bestKnown = closed.get(current);
            if (bestKnown != null && bestKnown <= current.g)
                continue;
            // Otherwise, record this state in the closed set with the current cost 'g'.
            closed.put(current, current.g);

            /* Generate neighboring states by attempting to push each crate in each of the four directions, while ensuring that the player
                can reach the necessary position to make the push and that the resulting state is valid. (Not a deadlock and within bounds.) */
            for (int crateIdx = 0; crateIdx < current.crates.length; crateIdx++) {
                // Get the current position of the crate to be pushed and calculate its row and column coordinates.
                int cratePos = current.crates[crateIdx];
                int crateRow = cratePos / width;
                int crateCol = cratePos % width;

                // Attempt to push the crate in each of the four directions. For each direction, calculate the new position of the crate and the required position of the player to make the push.
                for (int i = 0; i < 4; i++) {

                    int playerRow = crateRow - rowDirVectors[i];  //Position the player needs to be in to push the crate in the current direction
                    int playerCol = crateCol - colDirVectors[i];

                    int newcrateRow = crateRow + rowDirVectors[i];  // New position of the crate after the push
                    int newcrateCol = crateCol + colDirVectors[i];

                    // Check if push is illegal (out of bounds, hitting a wall, hitting a known deadlock, hitting another crate)
                    if (newcrateCol < 0 || newcrateRow < 0 || newcrateCol >= width || newcrateRow >= height)
                        continue;
                    if (mapData[newcrateRow][newcrateCol] == '#')
                        continue;
                    if (deadlockGrid[newcrateRow][newcrateCol])
                        continue;

                    if (current.hasCrateAt(newcrateCol, newcrateRow, width))
                        continue;

                    // Check if player can reach the required position to push the crate
                    if (playerCol < 0 || playerRow < 0 || playerCol >= width || playerRow >= height)
                        continue;
                    if (!reach.reachable[playerRow][playerCol]) continue;

                    // Create the new crate configuration resulting from the push and sort it for consistent hashing and comparison.
                    int[] newcrates = current.crates.clone();
                    newcrates[crateIdx] = newcrateRow * width + newcrateCol;
                    Arrays.sort(newcrates);

                    // Check if the new crate configuration results in a freeze deadlock. If it does, skip this state.
                    if (isFreezeDeadlock(newcrates, mapData, width)) continue;

                    // Calculate the heuristic for the new state. If the heuristic returns Integer.MAX_VALUE, it means this state is unsolvable, so skip it.
                    int nextH = calculateHeuristic(newcrates, targetDistGrids, width, targets);
                    if (nextH == Integer.MAX_VALUE) continue;

                    // Reconstruct the path for the player to reach the position required to push the crate and calculate the total cost of this move (the path to get there plus the push itself).
                    String walkPath = walkPath(playerRow, playerCol, reach);
                    int stepCost = (walkPath == null ? 0 : walkPath.length()) + 1;
                    String macroMove = (walkPath != null ? walkPath : "") + dirs[i];

                    // Create a new State for the neighboring state resulting from the push and add it to the open list for further exploration.
                    State neighbor = new State(
                            crateCol, crateRow, // Player ends up where the crate used to be
                            newcrates,
                            current.g + stepCost,
                            nextH, current, macroMove
                    );
                    openList.add(neighbor);
                }
            }
        }

        return "";  // If the open list is exhausted without finding a solution, return an empty string to indicate failure.
    }

    /**
     * Calculates the Heuristic (h): An estimate of how many pushes remain to solve the board.
     *
     * @param crates             An array of integers representing the current positions of the crates on the board. Each position is encoded as a single integer (row * width + column).
     * @param targetDistGrids   A 3D array where targetDistGrids[t][y][x] gives the minimum number of pushes required to move a crate from position (y, x) to target t, considering the static layout of the board and ignoring other crates.
     * @param width             The width of the Sokoban board, used to decode the 1D crate positions into 2D coordinates.
     * @param targets           An array of integers representing the positions of the target cells on the board. Each position is encoded as a single integer (row * width + column).
     * @return                  The heuristic estimate of the number of pushes required to solve the board from the current crate configuration. If any crate is in an unsolvable position (cannot reach any target), returns Integer.MAX_VALUE to indicate an unsolvable state.
     */
    private int calculateHeuristic(int[] crates, int[][][] targetDistGrids, int width, int[] targets) {

        // Checks if any crate is in a position from which it cannot reach any target. If such a crate exists, the heuristic returns Integer.MAX_VALUE to indicate that this state is unsolvable.
        for (int crate : crates) {
            int bx = crate % width;
            int by = crate / width;
            boolean canReachAny = false;
            for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                if (targetDistGrids[tIdx][by][bx] != Integer.MAX_VALUE) {
                    canReachAny = true;
                    break;
                }
            }
            if (!canReachAny) return Integer.MAX_VALUE;
        }

        // The heuristic calculation proceeds in two passes. The first pass ensures that any crates already on target positions are matched to those targets, which helps to maintain progress towards the goal.
        // The second pass greedily assigns remaining crates to the closest available targets while tracking conflicts. If a conflict arises (multiple crates wanting the same target), it adds a penalty to the heuristic to reflect the increased difficulty of resolving that conflict.
        boolean[] targetUsed = new boolean[targets.length];
        boolean[] crateMatched = new boolean[crates.length];
        int total = 0;

        // Pass 1: Ensure crates already settled on a target maintain their match
        for (int bIdx = 0; bIdx < crates.length; bIdx++) {
            int crate = crates[bIdx];
            for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                if (!targetUsed[tIdx] && targets[tIdx] == crate) {
                    targetUsed[tIdx] = true;
                    crateMatched[bIdx] = true;
                    break;
                }
            }
        }

        // Pass 2: Greedily map remaining crates to separate targets with conflict tracking
        for (int bIdx = 0; bIdx < crates.length; bIdx++) {
            if (crateMatched[bIdx]) continue;

            int crate = crates[bIdx];
            int bx = crate % width;
            int by = crate / width;

            int minDist = Integer.MAX_VALUE;
            int bestTargetIdx = -1;

            for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                if (targetUsed[tIdx]) continue;
                int d = targetDistGrids[tIdx][by][bx];
                if (d < minDist) {
                    minDist = d;
                    bestTargetIdx = tIdx;
                }
            }

            if (bestTargetIdx != -1) {
                total += minDist;
                targetUsed[bestTargetIdx] = true;
            } else {
                // If a target conflict exists, pull absolute minimum to any target and add a penalty
                int absMin = Integer.MAX_VALUE;
                for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                    int d = targetDistGrids[tIdx][by][bx];
                    if (d < absMin) absMin = d;
                }
                total += absMin + 200;
            }
        }

        return total;
    }

    /**
     * Performs a breadth-first search (BFS) to compute the reachability of the player from a given starting position,
     * considering the current crate configuration and the layout of the board. This method fills the Reachability object with information about
     * which cells are reachable, the parent pointers for path reconstruction, and the normalized player position for hashing.
     *
     * @param reach         The Reachability object to be filled with the results of the BFS, including reachable cells and parent pointers.
     * @param q             A reusable queue array for BFS traversal, where positions are encoded as single integers (row * width + column).
     * @param pRow          The starting row coordinate of the player for the reachability computation.
     * @param pCol          The starting column coordinate of the player for the reachability computation.
     * @param crates        An array of integers representing the positions of crates on the board.
     * @param mapData       A 2D array representing the layout of the board.
     * @param width         The width of the board.
     * @param height        The height of the board.
     */
    private void computeReachability( Reachability reach, int[] q, int pRow, int pCol,
                                      int[] crates, char[][] mapData, int width, int height) {

        // Initialize the Reachability object with the starting position of the player and reset all reachable cells and parent pointers.
        // The BFS will then explore the board to determine which cells the player can reach given the current crate configuration and map layout.
        reach.startX = pCol;
        reach.startY = pRow;

        // Reset reachability and parent pointers for the BFS
        for (int y = 0; y < height; y++) {
            Arrays.fill(reach.reachable[y], false);
            Arrays.fill(reach.parentX[y], -2);
            Arrays.fill(reach.parentY[y], -2);
        }

        // BFS initialization: Start from the player's current position, mark it as reachable, and set up the queue for traversal.
        // The normalized player position is initially set to the starting position's encoded value.
        int head = 0;
        int tail = 0;
        int startId = pRow * width + pCol;

        q[tail++] = startId;
        reach.reachable[pRow][pCol] = true;
        reach.parentX[pRow][pCol] = -1;
        reach.parentY[pRow][pCol] = -1;
        reach.normalizedPlayer = startId;

        int[] dy = {-1, 1, 0, 0};
        int[] dx = {0, 0, -1, 1};
        char[] dirs = {'u', 'd', 'l', 'r'};

        /* Perform BFS to explore the board and fill the Reachability object with information about which
           cells are reachable by the player, and updates the parent pointers accordingly. */
        while (head < tail) {
            int curr = q[head++];
            int cy = curr / width;
            int cx = curr % width;

            for (int i = 0; i < 4; i++) {
                int ny = cy + dy[i];
                int nx = cx + dx[i];

                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (mapData[ny][nx] == '#') continue;
                if (Arrays.binarySearch(crates, ny * width + nx) >= 0) continue;

                if (!reach.reachable[ny][nx]) {
                    reach.reachable[ny][nx] = true;
                    reach.parentX[ny][nx] = cx;
                    reach.parentY[ny][nx] = cy;
                    reach.parentMove[ny][nx] = dirs[i];
                    int nextId = ny * width + nx;
                    if (nextId < reach.normalizedPlayer) {
                        reach.normalizedPlayer = nextId;
                    }
                    q[tail++] = nextId;
                }
            }
        }
    }

    /**
     * Traces the footsteps of the player across individual floor tiles.
     * This method reads the 2D grid history stored during a reachability scan to
     * reconstruct the exact sequence of empty-floor movements (e.g., "uulld") required
     * for the player to walk from their current position to a specific tile behind a crate.
     *
     * @param tx    The target column coordinate (X) the player wants to reach.
     * @param ty    The target row coordinate (Y) the player wants to reach.
     * @param reach The Reachability state cache tracking visited tiles and their parents.
     * @return      A string of directional characters ('u', 'd', 'l', 'r'), or null if unreachable.
     */
    private String walkPath(int ty, int tx, Reachability reach) {
        if (!reach.reachable[ty][tx]) return null;

        StringBuilder path = new StringBuilder();
        int cx = tx, cy = ty;

        while (cx != reach.startX || cy != reach.startY) {
            path.append(reach.parentMove[cy][cx]);
            int px = reach.parentX[cy][cx];
            int py = reach.parentY[cy][cx];
            cx = px;
            cy = py;
        }

        return path.reverse().toString();
    }

    /**
     * Precomputes the distance grids for each target, where each grid cell contains the minimum number of pushes required to move a crate from that cell to the target,
     * considering the static layout of the board and ignoring other crates. This precomputation allows for efficient heuristic calculations during the A* search.
     * Uses BFS starting from each target to fill the distance grids, ensuring that the distances reflect the actual push requirements based on the board's layout and walls.
     */
    private int[][][] computeTargetDistGrids(int width, int height, char[][] mapData, int[] targets) {
        // Initialize a 3D array to store the distance grids for each target. Each grid will be filled with the minimum number of pushes required to move a crate from any cell to that target, or Integer.MAX_VALUE if it's unreachable.
        int[][][] distGrids = new int[targets.length][height][width];
        int[] q = new int[width * height];

        // Direction vectors for moving up, down, left, and right.
        int[] dy = {-1, 1, 0, 0};
        int[] dx = {0, 0, -1, 1};

        /* For each target, perform a BFS to fill the corresponding distance grid. The BFS starts from the target position and explores
            the board to determine how many pushes are required to move a crate from any cell to that target, considering the layout of
            walls and valid push directions. */
        for (int tIdx = 0; tIdx < targets.length; tIdx++) {
            int t = targets[tIdx];
            for (int r = 0; r < height; r++) {
                Arrays.fill(distGrids[tIdx][r], Integer.MAX_VALUE);
            }

            // Initialize BFS from the target position, marking it as distance 0 and adding it to the queue.
            int head = 0, tail = 0;
            int r = t / width;
            int c = t % width;
            distGrids[tIdx][r][c] = 0;
            q[tail++] = t;

            // Perform BFS to fill the distance grid for the current target. For each cell, check the four possible push directions and update the distances accordingly.
            while (head < tail) {
                int curr = q[head++];
                int cy = curr / width;
                int cx = curr % width;
                int currDist = distGrids[tIdx][cy][cx];

                for (int i = 0; i < 4; i++) {
                    int ny = cy - dy[i]; // Where the crate would be pulled from
                    int nx = cx - dx[i];
                    int py = ny - dy[i]; // Where the player must stand to pull it
                    int px = nx - dx[i];

                    if (nx >= 0 && nx < width && ny >= 0 && ny < height && mapData[ny][nx] != '#' &&
                            px >= 0 && px < width && py >= 0 && py < height && mapData[py][px] != '#') {
                        if (distGrids[tIdx][ny][nx] == Integer.MAX_VALUE) {
                            distGrids[tIdx][ny][nx] = currDist + 1;
                            q[tail++] = ny * width + nx;
                        }
                    }
                }
            }
        }
        return distGrids;
    }

    /**
     * Checks for freeze deadlocks in the current crate configuration. A freeze deadlock occurs when a crate is pushed into a position
     * where it cannot be moved anymore.
     * @param crates     An array of integers representing the current positions of the crates on the board. Each position is encoded as a single integer (row * width + column).
     * @param mapData    A 2D array representing the layout of the board, where walls are denoted by '#' and empty spaces by '.'.
     * @param width      The width of the board, used to decode the 1D crate positions into 2D coordinates.
     * @return           Returns false if no deadlock is found
     */
    private boolean isFreezeDeadlock(int[] crates, char[][] mapData, int width) {
        // Check each crate to see if it is in a position where it cannot be moved.
        for (int bPos : crates) {
            int bx = bPos % width;
            int by = bPos / width;
            if (mapData[by][bx] == '.') continue;

            boolean up = isBlocked(bx, by - 1, crates, mapData, width);
            boolean down = isBlocked(bx, by + 1, crates, mapData, width);
            boolean left = isBlocked(bx - 1, by, crates, mapData, width);
            boolean right = isBlocked(bx + 1, by, crates, mapData, width);

            if (up && left && isBlocked(bx - 1, by - 1, crates, mapData, width)) return true;
            if (up && right && isBlocked(bx + 1, by - 1, crates, mapData, width)) return true;
            if (down && left && isBlocked(bx - 1, by + 1, crates, mapData, width)) return true;
            if (down && right && isBlocked(bx + 1, by + 1, crates, mapData, width)) return true;
        }
        return false;
    }

    /**
     * A helper method to determine if a given cell is blocked for the player, either by a wall or by a crate.
     * This is used in both the reachability computation and the freeze deadlock check to determine if the player can move into
     * a cell or if a crate can be moved into a cell.
     *
     * @param x         The column coordinate of the cell to check.
     * @param y         The row coordinate of the cell to check.
     * @param crates     An array of integers representing the positions of crates on the board, used to check if a crate is occupying the cell.
     * @param mapData   A 2D array representing the layout of the board, where walls are denoted by '#' and empty spaces by '.'.
     * @param width     The width of the board, used to decode the 1D crate positions into 2D coordinates for comparison.
     */
    private boolean isBlocked(int x, int y, int[] crates, char[][] mapData, int width) {
        if (y < 0 || y >= mapData.length || x < 0 || x >= mapData[0].length)
            return true;
        if (mapData[y][x] == '#')
            return true;
        // Check if a crate is occupying the cell by performing a binary search on the sorted array of crate positions.
        return Arrays.binarySearch(crates, y * width + x) >= 0;
    }

    /**
     * Precomputes deadlock positions on the board. A deadlock position is a cell where if a crate is pushed into it, it cannot be moved out,
     * making the puzzle unsolvable from that state.
     *
     * @param width     The width of the board.
     * @param height    The height of the board.
     * @param mapData   A 2D array representing the layout of the board.
     * @param targets   A list of integers representing the positions of target cells.
     * @return          A 2D boolean array indicating the deadlock positions.
     */
    private boolean[][] precomputeDeadlocks(int width, int height, char[][] mapData, List<Integer> targets) {
        // Initialize a 2D boolean array to mark deadlock positions on the board. A cell will be marked as true if it is a deadlock position.
        boolean[][] deadlocks = new boolean[height][width];
        Set<Integer> targetSet = new HashSet<>(targets);

        /* First pass: Identify basic deadlock positions based on walls and the absence of targets.
            A cell is marked as a deadlock if it is not a wall or a target and is adjacent to walls in such a way that a crate pushed there would be stuck. */
        for (int r = 1; r < height - 1; r++) {
            // Skip the outermost rows and columns since they are typically walls and cannot be deadlock positions for crates.
            for (int c = 1; c < width - 1; c++) {
                if (mapData[r][c] == '#' || targetSet.contains(r * width + c)) continue;

                boolean wallUp = mapData[r - 1][c] == '#';
                boolean wallDown = mapData[r + 1][c] == '#';
                boolean wallLeft = mapData[r][c - 1] == '#';
                boolean wallRight = mapData[r][c + 1] == '#';

                if ((wallUp && wallLeft) || (wallUp && wallRight) || (wallDown && wallLeft) || (wallDown && wallRight)) {
                    deadlocks[r][c] = true;
                }
            }
        }

        /* Second pass: Extend deadlock detection to include positions that are part of a chain of deadlocks. */
        for (int r = 1; r < height - 1; r++) {
            for (int c = 1; c < width - 1; c++) {
                if (deadlocks[r][c]) {
                    if (mapData[r - 1][c] == '#' || mapData[r + 1][c] == '#') {
                        markEdgeDeadlocks(r, c, 0, 1, width, mapData, targetSet, deadlocks);
                    }
                    if (mapData[r][c - 1] == '#' || mapData[r][c + 1] == '#') {
                        markEdgeDeadlocks(r, c, 1, 0, width, mapData, targetSet, deadlocks);
                    }
                }
            }
        }
        return deadlocks;
    }

    /**
     * Helper for finding Edge Deadlocks. Follows a wall straight until it hits another wall or a target.
     * If it hits another wall (corner) without seeing a target, that entire edge is a deadlock.
     */
    private void markEdgeDeadlocks(int startRow, int startCol, int rowDirection, int colDirection,
                                   int strideWidth, char[][] wallGrid, Set<Integer> targetPositions,
                                   boolean[][] deadlockGrid) {
        int totalRows = wallGrid.length;
        int totalCols = wallGrid[0].length;

        // Begin walking from the tile immediately next to the initial corner
        int currRow = startRow + rowDirection;
        int currCol = startCol + colDirection;
        List<int[]> tilesOnEdge = new ArrayList<>();

        // Keep walking in a straight line until we hit a wall or go out of map boundaries
        while (currRow >= 0 && currRow < totalRows && currCol >= 0 && currCol < totalCols && wallGrid[currRow][currCol] != '#') {
            // If this edge tile contains a target, a crate pushed here can still win. The edge is safe
            if (targetPositions.contains(currRow * strideWidth + currCol)) return;

            // Initialize flags to check for walls on either side of this edge tile
            boolean hasWallOnSideA = false;
            boolean hasWallOnSideB = false;

            // Check the flanking tiles parallel to our line of movement.
            // A crate is only trapped on an edge if there is a solid wall running right alongside it.
            int flankRowA = currRow - colDirection;
            int flankColA = currCol - rowDirection;
            if (flankRowA >= 0 && flankRowA < totalRows && flankColA >= 0 && flankColA < totalCols) {
                hasWallOnSideA = wallGrid[flankRowA][flankColA] == '#';
            }

            int flankRowB = currRow + colDirection;
            int flankColB = currCol + rowDirection;
            if (flankRowB >= 0 && flankRowB < totalRows && flankColB >= 0 && flankColB < totalCols) {
                hasWallOnSideB = wallGrid[flankRowB][flankColB] == '#';
            }

            // If there is open space on BOTH sides of the path. It's not a deadlock edge.
            if (!hasWallOnSideA && !hasWallOnSideB) return;

            // If we connect to another known deadlock (like the opposite corner) without hitting a target,
            // then every single tile we stepped on along this wall is a permanent trap.
            if (deadlockGrid[currRow][currCol]) {
                for (int[] tile : tilesOnEdge) {
                    deadlockGrid[tile[0]][tile[1]] = true;
                }
                return;
            }

            // Queue this tile up to be marked as a deadlock later if the whole edge checks out
            tilesOnEdge.add(new int[]{currRow, currCol});

            // Advance one step forward in our designated direction
            currRow += rowDirection;
            currCol += colDirection;
        }
    }
}