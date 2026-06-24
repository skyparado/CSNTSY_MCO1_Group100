package solver;

import reader.FileReader;
import reader.MapData;

public class SokoBotTest {

    static final String[] MAPS = {
        "twoboxes1", "twoboxes2", "twoboxes3",
        "threeboxes1", "threeboxes2", "threeboxes3",
        "fourboxes1", "fourboxes2", "fourboxes3",
        "fiveboxes1", "fiveboxes2", "fiveboxes3",
        "original1", "original2", "original3",
        "deadlocktest", "testlevel",
        "custom1", "custom2", "custom3", "custom4",
        "custom5", "custom6", "custom7", "custom8"
    };

    static final int TIMEOUT_MS = 15000;

    public static void main(String[] args) {
        System.out.printf("%-15s  %-8s  %s%n", "MAP", "RESULT", "TIME");
        System.out.println("-".repeat(40));

        int solved = 0, failed = 0, errors = 0;

        for (String mapName : MAPS) {
            FileReader fr = new FileReader();
            MapData md = fr.readFile(mapName);

            if (md == null) {
                System.out.printf("%-15s  %-8s%n", mapName, "NOT FOUND");
                errors++;
                continue;
            }

            int rows = md.rows;
            int cols = md.columns;
            char[][] map = new char[rows][cols];
            char[][] items = new char[rows][cols];

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    switch (md.tiles[i][j]) {
                        case '#': map[i][j] = '#'; items[i][j] = ' '; break;
                        case '@': map[i][j] = ' '; items[i][j] = '@'; break;
                        case '$': map[i][j] = ' '; items[i][j] = '$'; break;
                        case '.': map[i][j] = '.'; items[i][j] = ' '; break;
                        case '+': map[i][j] = '.'; items[i][j] = '@'; break;
                        case '*': map[i][j] = '.'; items[i][j] = '$'; break;
                        default:  map[i][j] = ' '; items[i][j] = ' '; break;
                    }
                }
            }

            final char[][] mapFinal = map;
            final char[][] itemsFinal = items;
            final int colsFinal = cols;

            final String[] result = {null};
            Thread t = new Thread(() -> {
                result[0] = new SokoBot().solveSokobanPuzzle(colsFinal, rows, mapFinal, itemsFinal);
            });

            long start = System.currentTimeMillis();
            t.start();
            try {
                t.join(TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long elapsed = System.currentTimeMillis() - start;

            if (t.isAlive()) {
                t.interrupt();
                System.out.printf("%-15s  %-8s  >%ds%n", mapName, "TIMEOUT", TIMEOUT_MS / 1000);
                failed++;
            } else if (result[0] == null || result[0].isEmpty()) {
                System.out.printf("%-15s  %-8s  %dms%n", mapName, "NO SOL", elapsed);
                failed++;
            } else {
                System.out.printf("%-15s  %-8s  %dms%n", mapName, "SOLVED", elapsed);
                solved++;
            }
        }

        System.out.println("-".repeat(40));
        System.out.printf("SOLVED: %d / %d   FAILED/TIMEOUT: %d%n",
            solved, MAPS.length - errors, failed);
    }
}
