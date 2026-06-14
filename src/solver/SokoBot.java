package solver;

import java.util.*;

public class SokoBot {

  public String solveSokobanPuzzle(int width, int height, char[][] mapData, char[][] itemsData) {

    List<int[]> goals = new ArrayList<>();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (mapData[y][x] == '.') {
          goals.add(new int[]{x, y});
        }
      }
    }

    int startX = 0, startY = 0;
    Set<Integer> startCrates = new HashSet<>();

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (itemsData[y][x] == '@') {
          startX = x;
          startY = y;
        } else if (itemsData[y][x] == '$') {
          startCrates.add(State.pack(x, y));
        }
      }
    }

    State startState = new State(startX, startY, startCrates, '\0', null);

    if (startState.isGoal(mapData)) {
      return "";
    }

    PriorityQueue<Object[]> frontier = new PriorityQueue<>(
        Comparator.comparingInt(entry -> (int) entry[0])
    );

    Map<State, Integer> bestG = new HashMap<>();
    bestG.put(startState, 0);

    frontier.offer(new Object[]{heuristic(startState, goals), 0, startState});

    Set<State> closed = new HashSet<>();

    while (!frontier.isEmpty()) {
      Object[] entry = frontier.poll();
      int currentG = (int) entry[1];
      State current = (State) entry[2];

      if (closed.contains(current)) continue;
      closed.add(current);

      if (current.isGoal(mapData)) {
        return current.reconstructPath();
      }

      for (State neighbor : current.getValidMoves(mapData, width, height)) {
        if (closed.contains(neighbor)) continue;

        if (isDeadlock(neighbor, mapData)) continue; // To check Deadlock States L states

        int newG = currentG + 1;
        Integer knownG = bestG.get(neighbor);
        if (knownG != null && newG >= knownG) continue;

        bestG.put(neighbor, newG);
        int f = newG + heuristic(neighbor, goals);
        frontier.offer(new Object[]{f, newG, neighbor});
      }
    }

    return "";
  }

  private int heuristic(State state, List<int[]> goals) {
    int total = 0;
    for (int packed : state.crates) {
      int crateX = packed / 1000;
      int crateY = packed % 1000;
      int minDist = Integer.MAX_VALUE;
      for (int[] goal : goals) {
        int dist = Math.abs(crateX - goal[0]) + Math.abs(crateY - goal[1]);
        if (dist < minDist) minDist = dist;
      }
      total += minDist;
    }
    return total;
  }

  private boolean isDeadlock(State state, char[][] map) {
    for (int packed : state.crates) {
        int x = packed / 1000;
        int y = packed % 1000;

        // Checks if crate is on goal
        if (map[y][x] == '.') continue;

        //Check if each direction is blocked by a wall either up,down,left or right
        boolean up = isWall(map, x, y - 1);
        boolean down = isWall(map, x, y + 1);
        boolean left = isWall(map, x - 1, y);
        boolean right = isWall(map, x + 1, y);

        // L shape deadlock: crate is against two walls that are perpendicular
        if ((up || down) && (left || right)) {
            return true;
        }
    }
    return false;
}

// Just a helper function to check if a cell is a wall or out of bounds
private boolean isWall(char[][] map, int x, int y) {
    if (y < 0 || y >= map.length || x < 0 || x >= map[0].length) {
        return true;
    }
    return map[y][x] == '#';
}

}
