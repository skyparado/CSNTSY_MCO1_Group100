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
}
