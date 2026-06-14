package solver;

import java.util.*;

public class SokoBot {

    /**
     * Represents a single node or stateof the game during the solving process.
     * Every time a box is pushed, a new SearchNode is created.
     */
    static class SearchNode implements Comparable<SearchNode> {
        final int[] boxes;      // An array storing the 1D board positions of every box.
        final int playerRow;    // The current row coordinate of the player in this state.
        final int playerCol;    // The current column coordinate of the player in this state.

        final int f;            // The total estimated cost (g + weighted h) for reaching the goal from the start through this state.
        final int g;            // The actual cost from the start state to this state.
        final int h;            // The heuristic estimate from this state to the goal.

        final SearchNode parent; // A reference to the parent SearchNode, used for reconstructing the solution path once the goal is reached.
        final String macroMove;  // The macro move (a sequence of player movements followed by a push) that leads from the parent state to this state.   

        /**
         * Constructor for creating a new SearchNode.
         * This constructor initializes all the fields of the SearchNode, including calculating the total estimated cost 'f' using a weighted heuristic approach.
         * 
         * @param boxes         An array of integers representing the positions of the boxes on the board. Each position is encoded as a single integer (row * width + column).
         * @param playerRow     The row coordinate of the player in this state.
         * @param playerCol     The column coordinate of the player in this state.
         * @param g             The actual cost from the start state to this state.
         * @param h             The heuristic estimate from this state to the goal.
         * @param parent        A reference to the parent SearchNode.
         * @param macroMove     The macro move that leads from the parent state to this state.
         */
        public SearchNode(int[] boxes, int playerRow, int playerCol,
                          int g, int h, SearchNode parent, String macroMove) {

            this.boxes = boxes;
            this.playerRow = playerRow;
            this.playerCol = playerCol;
            this.g = g;
            this.h = h;
            
            // WEIGHTED A*: Prioritizes closer-to-goal states with refined weights
            this.f = g + (4 * h); 
            
            this.parent = parent;
            this.macroMove = macroMove;
        }

        /**
         * Compares this SearchNode with another SearchNode for ordering in a priority queue.
         * The comparison is primarily based on the total estimated cost 'f'. If two nodes have
         * the same 'f' value, the tie is broken by preferring the node with a larger 'g' value, 
         * which indicates a deeper or closer-to-goal state. 
         * 
         * This tie-breaking strategy helps to navigate plateaus in the search space more effectively by favoring 
         * states that have already made more progress towards the goal.
         */
        @Override
        public int compareTo(SearchNode other) {
            int cmp = Integer.compare(this.f, other.f);
            if (cmp == 0) {
                // Prefer states with a larger 'g' (deeper/closer to goal) to break plateaus greedily
                return Integer.compare(other.g, this.g);
            }
            return cmp;
        }
    }

    /** 
     * A helper class used as a key in the closed set to track visited states. 
     * It encapsulates the positions of the boxes and a normalized player position.
     */
    static class HashKey {

        final int[] boxes;              // An array representing the positions of the boxes, sorted to ensure consistent hashing regardless of box order.
        final int normalizedPlayer;     // A normalized representation of the player's position.

        // The constructor initializes the HashKey with the given box positions and normalized player position.
        public HashKey(int[] boxes, int normalizedPlayer) {
            this.boxes = boxes;
            this.normalizedPlayer = normalizedPlayer;
        }

        // The equals method checks if two HashKey objects are equal by comparing their normalized player positions and the arrays of box positions.
        @Override
        public boolean equals(Object o) {
            
            if (this == o) 
                return true;
            if (!(o instanceof HashKey))  
                return false;

            HashKey k = (HashKey) o;

            // Two states are considered equal if they have the same normalized player position and identical box configurations (regardless of the order of boxes).
            return normalizedPlayer == k.normalizedPlayer && Arrays.equals(boxes, k.boxes);
        }

        // The hashCode method generates a hash code for the HashKey object by combining the hash of the normalized player position and the hash of the boxes array.
        @Override
        public int hashCode() {
            return 31 * normalizedPlayer + Arrays.hashCode(boxes);
        }
    }

    /**
     * A helper class used to compute and store the reachability information of the player from a given position, considering the current box configuration.
     * 
     * This class contains a 2D boolean array 'reachable' that indicates which cells the player can reach, 
     * as well as parent pointers to reconstruct paths and a normalized player position for hashing purposes.
     */
    private static class Reachability {
        
        int startX, startY;             // The starting coordinates of the player for the reachability computation.
        final boolean[][] reachable;    // A 2D array indicating whether each cell is reachable by the player given the current box configuration and map layout.
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
     * and the items data representing the initial positions of boxes and the player.
     * 
     * @param width         The width of the Sokoban board.
     * @param height        The height of the Sokoban board.
     * @param mapData       A 2D array of characters representing the static elements of the board, such as walls ('#') and targets ('.').
     * @param itemsData     A 2D array of characters representing the dynamic elements of the board, such as the player's position ('@' or '+') and box positions ('$' or '*').
     * @return
     */ 
    public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {

        // Step 1: Parse the input to identify target positions, initial box positions, and the player's starting position.

        List<Integer> targetList = new ArrayList<>();   // A list to store the 1D positions of target cells on the board.
        List<Integer> startBoxList = new ArrayList<>(); // A list to store the 1D positions of the boxes at the start of the puzzle.

        // Variables to store the starting position of the player. Initialized to -1 to indicate that they have not been set yet.
        int startPRow = -1, startPCol = -1;

        // Scan the map and record where targets, boxes, and the player are located.        
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {

                int posId = r * width + c;  // Convert 2D coordinates to a single integer for easier storage and comparison.
                if (mapData[r][c] == '.') targetList.add(posId); 
                
                if (itemsData[r][c] == '@' || itemsData[r][c] == '+') {
                    startPRow = r;
                    startPCol = c;
                } else if (itemsData[r][c] == '$' || itemsData[r][c] == '*') {
                    startBoxList.add(posId);
                }
            }
        }

        // Convert the lists of targets and boxes to arrays and sort them for consistent ordering, which is crucial for hashing and comparisons.
        int[] targets = targetList.stream().mapToInt(i -> i).toArray();
        Arrays.sort(targets);
        
        int[] startBoxes = startBoxList.stream().mapToInt(i -> i).toArray();
        Arrays.sort(startBoxes);

        // If the initial configuration already matches the target configuration, return an empty string as no moves are needed.
        if (Arrays.equals(startBoxes, targets)) return ""; 

        // Step 2: Precompute deadlock positions to quickly identify states that cannot lead to a solution, thus pruning the search space effectively.
        boolean[][] deadlockGrid = precomputeDeadlocks(width, height, mapData, targetList);
        
        // Generate target-specific pull distance grids
        int[][][] targetDistGrids = computeTargetDistGrids(width, height, mapData, targets);

        // Step 3: Initialize the A* search algorithm with the starting state, including the heuristic calculation for the initial configuration.
        int initialH = calculateHeuristic(startBoxes, targetDistGrids, width, targets);
        if (initialH == Integer.MAX_VALUE) return ""; 

        // Create the initial SearchNode representing the starting state of the puzzle, with the player's position, box positions, and heuristic cost.
        SearchNode startNode = new SearchNode(startBoxes, startPRow, startPCol, 0, initialH, null, "");

        // Initialize the open list (priority queue) for A* search and the closed set (hash map) to track visited states. 
        // The open list will prioritize nodes based on their estimated total cost 'f'.
        PriorityQueue<SearchNode> openList = new PriorityQueue<>();
        Map<HashKey, Integer> closed = new HashMap<>();
        openList.add(startNode);

        // Direction vectors and corresponding move characters for navigating the board. 
        // These will be used to generate new states based on player movements and box pushes.
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
            // Poll the node with the lowest estimated total cost 'f' from the open list to explore next.
            SearchNode current = openList.poll();

            // Check if the time limit has been exceeded, if so, return an empty string to indicate that no solution was found within the time constraints.
            if (++iterations % 1000 == 0 && (System.currentTimeMillis() - startTime > TIME_LIMIT_MS)) {
                return ""; 
            }

            //  If the current configuration of boxes matches the target configuration, reconstruct and return the path of moves that led to this solution.
            if (Arrays.equals(current.boxes, targets)) {
                return traceMoves(current);
            }

            /* Compute the reachability of the player from the current position, given the current box configuration. 
                This is essential for determining which moves are possible from this state. */
            computeReachability(reach, reachQueue, current.playerRow, current.playerCol, current.boxes, mapData, width, height);
           
            /*  Create a HashKey for the current state to check against the closed set. 
                This helps to avoid re-exploring states that have already been visited with a lower or equal cost. */
            HashKey key = new HashKey(current.boxes, reach.normalizedPlayer);

            // Check if this state has been visited before with a lower or equal cost. If so, skip processing this node.
            Integer bestKnown = closed.get(key); 
                if (bestKnown != null && bestKnown <= current.g) 
                    continue;
            // Otherwise, record this state in the closed set with the current cost 'g'.    
            closed.put(key, current.g);

            /*  Generate neighboring states by attempting to push each box in each of the four directions, while ensuring that the player 
                can reach the necessary position to make the push and that the resulting state is valid (not a deadlock and within bounds). */
            for (int boxIdx = 0; boxIdx < current.boxes.length; boxIdx++) {
                // Get the current position of the box to be pushed, and calculate its row and column coordinates.
                int boxPos = current.boxes[boxIdx];
                int boxRow = boxPos / width;
                int boxCol = boxPos % width;

                // Attempt to push the box in each of the four directions. For each direction, calculate the new position of the box and the required position of the player to make the push.
                for (int i = 0; i < 4; i++) {
                    
                    int playerRow = boxRow - rowDirVectors[i];  //Position the player needs to be in to push the box in the current direction
                    int playerCol = boxCol - colDirVectors[i];

                    int newBoxRow = boxRow + rowDirVectors[i];  // New position of the box after the push
                    int newBoxCol = boxCol + colDirVectors[i];

                   // Check if push is illegal (out of bounds, hitting a wall, hitting a known deadlock, hitting another box)
                    if (newBoxCol < 0 || newBoxRow < 0 || newBoxCol >= width || newBoxRow >= height) 
                        continue;
                    if (mapData[newBoxRow][newBoxCol] == '#') 
                        continue;
                    if (deadlockGrid[newBoxRow][newBoxCol]) 
                        continue;
                    if (Arrays.binarySearch(current.boxes, newBoxRow * width + newBoxCol) >= 0) //
                        continue;

                    // Check if player can reach the required position to push the box
                    if (playerCol < 0 || playerRow < 0 || playerCol >= width || playerRow >= height) 
                        continue;
                    if (!reach.reachable[playerRow][playerCol]) continue;

                   // Create the new box configuration resulting from the push, and sort it for consistent hashing and comparison.                    
                    int[] newBoxes = current.boxes.clone();
                    newBoxes[boxIdx] = newBoxRow * width + newBoxCol;
                    Arrays.sort(newBoxes);

                    // Check if the new box configuration results in a freeze deadlock. If it does, skip this state.
                    if (isFreezeDeadlock(newBoxes, mapData, width)) continue;

                    // Calculate the heuristic for the new state. If the heuristic returns Integer.MAX_VALUE, it means this state is unsolvable, so skip it.
                    int nextH = calculateHeuristic(newBoxes, targetDistGrids, width, targets);
                    if (nextH == Integer.MAX_VALUE) continue; 

                    // Reconstruct the path for the player to reach the position required to push the box, and calculate the total cost of this move (the path to get there plus the push itself).
                    String walkPath = walkPath(playerRow, playerCol, reach);
                    int stepCost = (walkPath == null ? 0 : walkPath.length()) + 1;
                    String macroMove = (walkPath != null ? walkPath : "") + dirs[i];

                    // Create a new SearchNode for the neighboring state resulting from the push, and add it to the open list for further exploration.
                    SearchNode neighbor = new SearchNode(
                        newBoxes, boxRow, boxCol, 
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
     * Traces the macro-level history of the game across distinct puzzle states.
     * This method traverses backward through linked search nodes—where each node 
     * represents a snapshot of the board after a box push—to compile the overarching, 
     * complete sequence of macro maneuvers that solves the entire puzzle.
     *
     * @param node The final SearchNode containing the winning game layout.
     * @return     The unified, step-by-step solution string containing all combined moves.
     */
    private String traceMoves(SearchNode node) {
        List<String> steps = new ArrayList<>();
        
        // Follow parent pointers backward from the winning layout to the initial layout
        while (node != null && !node.macroMove.isEmpty()) {
            steps.add(node.macroMove);
            node = node.parent;
        }

        // Reverse the chronological history so the steps play forward from the start
        Collections.reverse(steps); 
        
        StringBuilder sb = new StringBuilder();
        for (String s : steps) {
            sb.append(s);
        }

        return sb.toString();
    }

    /**
     * Calculates the Heuristic (h): An estimate of how many pushes remain to solve the board.
     * 
     * @param boxes             An array of integers representing the current positions of the boxes on the board. Each position is encoded as a single integer (row * width + column).
     * @param targetDistGrids   A 3D array where targetDistGrids[t][y][x] gives the minimum number of pushes required to move a box from position (y, x) to target t, considering the static layout of the board and ignoring other boxes.
     * @param width             The width of the Sokoban board, used to decode the 1D box positions into 2D coordinates.
     * @param targets           An array of integers representing the positions of the target cells on the board. Each position is encoded as a single integer (row * width + column).
     * @return                  The heuristic estimate of the number of pushes required to solve the board from the current box configuration. If any box is in an unsolvable position (cannot reach any target), returns Integer.MAX_VALUE to indicate an unsolvable state.
     */
    private int calculateHeuristic(int[] boxes, int[][][] targetDistGrids, int width, int[] targets) {
        
        // Checks if any box is in a position from which it cannot reach any target. If such a box exists, the heuristic returns Integer.MAX_VALUE to indicate that this state is unsolvable.
        for (int box : boxes) {
            int bx = box % width;
            int by = box / width;
            boolean canReachAny = false;
            for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                if (targetDistGrids[tIdx][by][bx] != Integer.MAX_VALUE) {
                    canReachAny = true;
                    break;
                }
            }
            if (!canReachAny) return Integer.MAX_VALUE;
        }

        // The heuristic calculation proceeds in two passes. The first pass ensures that any boxes already on target positions are matched to those targets, which helps to maintain progress towards the goal. 
        // The second pass greedily assigns remaining boxes to the closest available targets while tracking conflicts. If a conflict arises (multiple boxes wanting the same target), it adds a penalty to the heuristic to reflect the increased difficulty of resolving that conflict.
        boolean[] targetUsed = new boolean[targets.length];
        boolean[] boxMatched = new boolean[boxes.length];
        int total = 0;

        // Pass 1: Ensure boxes already settled on a target maintain their match
        for (int bIdx = 0; bIdx < boxes.length; bIdx++) {
            int box = boxes[bIdx];
            for (int tIdx = 0; tIdx < targets.length; tIdx++) {
                if (!targetUsed[tIdx] && targets[tIdx] == box) {
                    targetUsed[tIdx] = true;
                    boxMatched[bIdx] = true;
                    break;
                }
            }
        }

        // Pass 2: Greedily map remaining boxes to separate targets with conflict tracking
        for (int bIdx = 0; bIdx < boxes.length; bIdx++) {
            if (boxMatched[bIdx]) continue;
            
            int box = boxes[bIdx];
            int bx = box % width;
            int by = box / width;

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
     * considering the current box configuration and the layout of the board. This method fills the Reachability object with information about 
     * which cells are reachable, the parent pointers for path reconstruction, and the normalized player position for hashing.
     * 
     * @param reach       The Reachability object to be filled with the results of the BFS, including reachable cells and parent pointers.
     * @param q           A reusable queue array for BFS traversal, where positions are encoded as single integers (row * width + column).
     * @param pRow        The starting row coordinate of the player for the reachability computation.
     * @param pCol        The starting column coordinate of the player for the reachability computation.
     * @param boxes       An array of integers representing the positions of boxes on the board.
     * @param mapData     A 2D array representing the layout of the board.
     * @param width       The width of the board.
     * @param height      The height of the board.
     */
    private void computeReachability( Reachability reach, int[] q, int pRow, int pCol,
                                      int[] boxes, char[][] mapData, int width, int height) {
       
        // Initialize the Reachability object with the starting position of the player and reset all reachable cells and parent pointers. 
        // The BFS will then explore the board to determine which cells the player can reach given the current box configuration and map layout.
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
                if (Arrays.binarySearch(boxes, ny * width + nx) >= 0) continue;

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
     * Traces the micro-level footsteps of the player across individual floor tiles.
     * This method reads the 2D grid history stored during a reachability scan to
     * reconstruct the exact sequence of empty-floor movements (e.g., "uulld") required 
     * for the player to walk from their current position to a specific tile behind a box.
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
     * Precomputes the distance grids for each target, where each grid cell contains the minimum number of pushes required to move a box from that cell to the target,
     * considering the static layout of the board and ignoring other boxes. This precomputation allows for efficient heuristic calculations during the A* search.
     * Uses BFS starting from each target to fill the distance grids, ensuring that the distances reflect the actual push requirements based on the board's layout and walls.
     * 
     * @param width
     * @param height
     * @param mapData
     * @param targets
     * @return
     */
    private int[][][] computeTargetDistGrids(int width, int height, char[][] mapData, int[] targets) {
        // Initialize a 3D array to store the distance grids for each target. Each grid will be filled with the minimum number of pushes required to move a box from any cell to that target, or Integer.MAX_VALUE if it's unreachable.
        int[][][] distGrids = new int[targets.length][height][width];
        int[] q = new int[width * height];
        
        // Direction vectors for moving up, down, left, and right. 
        int[] dy = {-1, 1, 0, 0};
        int[] dx = {0, 0, -1, 1};

        /* For each target, perform a BFS to fill the corresponding distance grid. The BFS starts from the target position and explores 
            the board to determine how many pushes are required to move a box from any cell to that target, considering the layout of 
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
                    int ny = cy - dy[i]; // Where the box would be pulled from
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
     * Checks for freeze deadlocks in the current box configuration. A freeze deadlock occurs when a box is pushed into a position 
     * where it cannot be moved anymore.
     * 
     * @param boxes      An array of integers representing the current positions of the boxes on the board. Each position is encoded as a single integer (row * width + column).
     * @param mapData    A 2D array representing the layout of the board, where walls are denoted by '#' and empty spaces by '.'.
     * @param width      The width of the board, used to decode the 1D box positions into 2D coordinates.
     * @return
     */
    private boolean isFreezeDeadlock(int[] boxes, char[][] mapData, int width) {
        // Check each box to see if it is in a position where it cannot be moved. 
        for (int bPos : boxes) {
            int bx = bPos % width;
            int by = bPos / width;
            if (mapData[by][bx] == '.') continue; 
            
            boolean up = isBlocked(bx, by - 1, boxes, mapData, width);
            boolean down = isBlocked(bx, by + 1, boxes, mapData, width);
            boolean left = isBlocked(bx - 1, by, boxes, mapData, width);
            boolean right = isBlocked(bx + 1, by, boxes, mapData, width);

            if (up && left && isBlocked(bx - 1, by - 1, boxes, mapData, width)) return true;
            if (up && right && isBlocked(bx + 1, by - 1, boxes, mapData, width)) return true;
            if (down && left && isBlocked(bx - 1, by + 1, boxes, mapData, width)) return true;
            if (down && right && isBlocked(bx + 1, by + 1, boxes, mapData, width)) return true;
        }
        return false;
    }

    /**
     * A helper method to determine if a given cell is blocked for the player, either by a wall or by a box. 
     * This is used in both the reachability computation and the freeze deadlock check to determine if the player can move into 
     * a cell or if a box can be moved into a cell.
     * @param x         The column coordinate of the cell to check.
     * @param y         The row coordinate of the cell to check.
     * @param boxes     An array of integers representing the positions of boxes on the board, used to check if a box is occupying the cell.
     * @param mapData   A 2D array representing the layout of the board, where walls are denoted by '#' and empty spaces by '.'.
     * @param width     The width of the board, used to decode the 1D box positions into 2D coordinates for comparison.
     * @return
     */
    private boolean isBlocked(int x, int y, int[] boxes, char[][] mapData, int width) {
        if (y < 0 || y >= mapData.length || x < 0 || x >= mapData[0].length) 
            return true;
        if (mapData[y][x] == '#') 
            return true;
        // Check if a box is occupying the cell by performing a binary search on the sorted array of box positions.
        return Arrays.binarySearch(boxes, y * width + x) >= 0;
    }

    /**
     * Precomputes deadlock positions on the board. A deadlock position is a cell where if a box is pushed into it, it cannot be moved out, 
     * making the puzzle unsolvable from that state. 
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
            A cell is marked as a deadlock if it is not a wall or a target and is adjacent to walls in such a way that a box pushed there would be stuck. */
        for (int r = 1; r < height - 1; r++) {
            // Skip the outermost rows and columns since they are typically walls and cannot be deadlock positions for boxes.
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
            // If this edge tile contains a target, a box pushed here can still win. The edge is safe
            if (targetPositions.contains(currRow * strideWidth + currCol)) return; 
        
            // Initialize flags to check for walls on either side of this edge tile
            boolean hasWallOnSideA = false;
            boolean hasWallOnSideB = false;

            // Check the flanking tiles parallel to our line of movement. 
            // A box is only trapped on an edge if there is a solid wall running right alongside it.
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