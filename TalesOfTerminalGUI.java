import javax.swing.*;
import java.awt.Color;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

// -------------------- MAIN CLASS --------------------
public class TalesOfTerminalGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                GameEngine engine = new GameEngine();
                engine.startGame();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}

// -------------------- GAME ENGINE --------------------
class GameEngine {
    private Player player;
    private GameWorld world;
    private GameFrame frame;
    private static final String RESULT_FILE = "tales_result.txt";

    public void startGame() throws IOException {
        String name = askPlayerName();
        player = new Player(name);
        world = new GameWorld(12, 8); // 12 cols x 8 rows
        frame = new GameFrame(player, world, this);
        frame.setVisible(true);
    }

    public void saveAndExit(String reason) {
        try {
            saveResult(reason);
            JOptionPane.showMessageDialog(frame, "Result saved to " + RESULT_FILE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Unable to save result: " + e.getMessage());
        }
        System.exit(0);
    }

    private String askPlayerName() {
        while (true) {
            String name = JOptionPane.showInputDialog(null, "Enter your player name:", "Welcome", JOptionPane.PLAIN_MESSAGE);
            if (name == null) System.exit(0);
            try {
                validateName(name);
                return name.trim();
            } catch (InvalidNameException ine) {
                JOptionPane.showMessageDialog(null, ine.getMessage(), "Invalid name", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void validateName(String name) throws InvalidNameException {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw new InvalidNameException("Name cannot be blank.");
        Pattern p = Pattern.compile("^[A-Za-z0-9 _-]{2,20}$");
        if (!p.matcher(trimmed).matches()) throw new InvalidNameException("Only letters, numbers, spaces, - and _ allowed (2-20 chars).");
    }

    private void saveResult(String reason) throws IOException {
        java.util.List<Enemy> enemies = world.getEnemies();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(RESULT_FILE, true))) {
            bw.write("=== TALES OF TERMINAL RESULT ===\n");
            bw.write("Player: " + player.getName() + "\n");
            bw.write("Reason: " + reason + "\n");
            bw.write("Score: " + player.getScore() + "\n");
            bw.write("HP: " + player.getHp() + "\n");
            bw.write("Steps: " + player.getSteps() + "\n");
            bw.write("Visited: " + player.getVisitedString() + "\n");
            bw.write("Inventory: " + String.join(", ", player.getInventory()) + "\n");
            bw.write("Remaining Enemies: " + enemies.size() + "\n");
            int i = 1;
            for (Enemy enemy : enemies) {
                Point p = enemy.getPosition();
                bw.write(String.format("  %d) %s at (%d,%d) power=%d dmg=%d\n", i++, enemy.getType(),
                        p.x, p.y, enemy.getPower(), enemy.getDamage()));
            }
            bw.write("---- End of Result ----\n\n");
        }
    }
}

// -------------------- CUSTOM EXCEPTION --------------------
class InvalidNameException extends Exception {
    public InvalidNameException(String msg) { super(msg); }
}

// -------------------- GAME FRAME --------------------
class GameFrame extends JFrame {
    private final GamePanel panel;
    private final Player player;
    private final GameWorld world;
    private final GameEngine engine;
    private final JLabel statusLabel = new JLabel();

    public GameFrame(Player player, GameWorld world, GameEngine engine) {
        super("Tales of Terminal - GUI");
        this.player = player;
        this.world = world;
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 740);
        setLocationRelativeTo(null);

        panel = new GamePanel(player, world, this::onAction);
        add(panel, java.awt.BorderLayout.CENTER);

        JPanel bottom = new JPanel(new java.awt.BorderLayout());
        statusLabel.setPreferredSize(new Dimension(360, 30));
        bottom.add(statusLabel, java.awt.BorderLayout.WEST);

        JButton saveBtn = new JButton("Save & Exit");
        saveBtn.addActionListener(e -> engine.saveAndExit("Manual Save & Exit"));
        bottom.add(saveBtn, java.awt.BorderLayout.EAST);

        add(bottom, java.awt.BorderLayout.SOUTH);
        refreshStatus();
    }

    public void refreshStatus() {
        statusLabel.setText(String.format(
                "Player: %s | HP: %d | Score: %d | Steps: %d | Enemies: %d",
                player.getName(), player.getHp(), player.getScore(), player.getSteps(), world.getEnemies().size()));
    }

    private void onAction(String action, Object data) {
        switch (action) {
            case "move":
                panel.repaint();
                break;
            case "search":
                searchAtPlayer();
                break;
            case "fight": {
                Enemy enemy = (Enemy) data;
                boolean won = player.fight(enemy);
                String msg;
                if (won) {
                    msg = "You defeated the enemy! +50 score, item: " + enemy.getDropItem();
                    world.removeEnemy(enemy);
                } else {
                    msg = "You were hit by enemy. HP: " + player.getHp();
                    if (!player.isAlive()) {
                        msg += "\nYou died. Game over.";
                        JOptionPane.showMessageDialog(this, msg);
                        engine.saveAndExit("Player Died");
                        return;
                    }
                }
                JOptionPane.showMessageDialog(this, msg);
                break;
            }
            case "collectedBooster": {
                String boosterName = (String) data;
                boolean removed = world.killOneEnemy();
                String info = removed ? "A nearby enemy was killed by the booster!" : "No enemies left to kill.";
                JOptionPane.showMessageDialog(this, "Collected booster: " + boosterName + "\n" + info);
                break;
            }
            case "reachedDestination":
                JOptionPane.showMessageDialog(this, "You reached the Destination! You win!");
                engine.saveAndExit("Reached Destination");
                return;
            case "playerDied":
                if (data instanceof Enemy) {
                    Enemy killer = (Enemy) data;
                    world.removeEnemy(killer);
                }
                engine.saveAndExit("Player Died");
                return;
        }
        refreshStatus();
    }

    private void searchAtPlayer() {
        Point p = player.getPosition();
        Optional<Enemy> oe = world.peekEnemyAt(p.x, p.y);
        if (oe.isPresent()) {
            Enemy e = oe.get();
            int opt = JOptionPane.showConfirmDialog(this, "Enemy found: " + e.getType() + ". Fight?", "Encounter", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) onAction("fight", e);
        } else {
            if (world.isBoosterAt(p.x, p.y)) {
                String b = world.collectBoosterAt(p.x, p.y);
                player.addToInventory(b);
                onAction("collectedBooster", b);
                panel.repaint();
            } else {
                JOptionPane.showMessageDialog(this, "No enemy or booster here.");
            }
        }
    }
}

// -------------------- ACTION HANDLER --------------------
@FunctionalInterface
interface ActionHandler {
    void handle(String action, Object data);
}

// -------------------- GAME PANEL --------------------
class GamePanel extends JPanel implements KeyListener {
    private final Player player;
    private final GameWorld world;
    private final ActionHandler handler;
    private final int cellSize = 56;
    private final int margin = 40;
    private final Set<Point> attackHighlights = Collections.synchronizedSet(new HashSet<>()); // flash cells

    // probabilities
    private static final double PLAYER_KILL_CHANCE_ON_MOVE = 0.65; // 65% chance player wins when moving into enemy
    private static final double ADJACENT_ATTACK_PROB = 0.6; // 60% chance an adjacent enemy attacks

    public GamePanel(Player player, GameWorld world, ActionHandler handler) {
        this.player = player;
        this.world = world;
        this.handler = handler;

        setBackground(Color.decode("#0B3D91"));
        setFocusable(true);
        addKeyListener(this);
        addHierarchyListener(e -> { if (isDisplayable()) requestFocusInWindow(); });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawGrid(g);
        drawCellsContent(g);
        drawEnemies(g);
        drawPlayer(g);
        drawHighlights(g);
        drawHUD(g);
    }

    private void drawGrid(Graphics g) {
        int cols = world.getCols();
        int rows = world.getRows();
        g.setColor(Color.decode("#E1EAF6"));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = margin + c * cellSize;
                int y = margin + r * cellSize;
                g.drawRect(x, y, cellSize, cellSize);
                if (player.hasVisited(c, r)) {
                    g.setColor(new Color(200, 230, 255, 80));
                    g.fillRect(x + 1, y + 1, cellSize - 1, cellSize - 1);
                    g.setColor(Color.decode("#E1EAF6"));
                }
            }
        }
    }

    private void drawCellsContent(Graphics g) {
        int cols = world.getCols();
        int rows = world.getRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = margin + c * cellSize;
                int y = margin + r * cellSize;
                if (world.isDestination(c, r)) {
                    g.setColor(Color.MAGENTA);
                    g.fillRect(x + 8, y + 8, cellSize - 16, cellSize - 16);
                    g.setColor(Color.WHITE);
                    g.drawString("D", x + cellSize / 2 - 4, y + cellSize / 2 + 4);
                } else if (world.isBoosterAt(c, r)) {
                    g.setColor(Color.ORANGE);
                    g.fillOval(x + 12, y + 12, cellSize - 24, cellSize - 24);
                    g.setColor(Color.BLACK);
                    g.drawString("B", x + cellSize / 2 - 4, y + cellSize / 2 + 4);
                }
            }
        }
    }

    private void drawEnemies(Graphics g) {
        for (Enemy enemy : world.getEnemies()) {
            Point pos = enemy.getPosition();
            int x = margin + pos.x * cellSize;
            int y = margin + pos.y * cellSize;
            g.setColor(Color.RED);
            g.fillOval(x + 8, y + 8, cellSize - 16, cellSize - 16);
            g.setColor(Color.BLACK);
            g.drawString("E", x + cellSize / 2 - 4, y + cellSize / 2 + 4);
        }
    }

    private void drawPlayer(Graphics g) {
        Point pos = player.getPosition();
        int x = margin + pos.x * cellSize;
        int y = margin + pos.y * cellSize;
        g.setColor(Color.GREEN);
        g.fillRect(x + 6, y + 6, cellSize - 12, cellSize - 12);
        g.setColor(Color.BLACK);
        g.drawString("P", x + cellSize / 2 - 4, y + cellSize / 2 + 4);
    }

    private void drawHighlights(Graphics g) {
        synchronized (attackHighlights) {
            g.setColor(new Color(255, 255, 0, 140)); // semi-transparent yellow
            for (Point p : attackHighlights) {
                int x = margin + p.x * cellSize;
                int y = margin + p.y * cellSize;
                g.fillRect(x + 1, y + 1, cellSize - 1, cellSize - 1);
            }
        }
    }

private void addHighlight(Point p) {
    attackHighlights.add(new Point(p));
    repaint();

    javax.swing.Timer t = new javax.swing.Timer(400, ev -> {
        attackHighlights.removeIf(pt -> pt.equals(p));
        repaint();
    });
    t.setRepeats(false);
    t.start();
}


    private void drawHUD(Graphics g) {
        g.setColor(Color.WHITE);
        g.drawString("Arrow Keys = Move | S = Search | I = Inventory", 30, 20);
        g.drawString("Score: " + player.getScore() + "  HP: " + player.getHp(), 600, 20);
    }

    @Override
    public void keyPressed(KeyEvent ev) {
        boolean moved = false;
        int key = ev.getKeyCode();
        switch (key) {
            case KeyEvent.VK_LEFT:  moved = player.move(-1, 0, world.getCols(), world.getRows()); break;
            case KeyEvent.VK_RIGHT: moved = player.move(1, 0, world.getCols(), world.getRows()); break;
            case KeyEvent.VK_UP:    moved = player.move(0, -1, world.getCols(), world.getRows()); break;
            case KeyEvent.VK_DOWN:  moved = player.move(0, 1, world.getCols(), world.getRows()); break;
            case KeyEvent.VK_S:     handler.handle("search", null); break;
            case KeyEvent.VK_I:     JOptionPane.showMessageDialog(this, "Inventory: " + player.getInventory()); break;
            default: break;
        }

        if (moved) {
            Point ppos = player.getPosition();

            // -------------------------------
            // 1) Player moved INTO enemy -> probabilistic result
            // -------------------------------
            Optional<Enemy> enemyAtDest = world.peekEnemyAt(ppos.x, ppos.y);
            if (enemyAtDest.isPresent()) {
                Enemy enemyFound = enemyAtDest.get();
                boolean playerWins = Math.random() < PLAYER_KILL_CHANCE_ON_MOVE;
                if (playerWins) {
                    world.removeEnemy(enemyFound);
                    player.addScore(50);
                    player.addToInventory(enemyFound.getDropItem());
                    addHighlight(enemyFound.getPosition());
                    JOptionPane.showMessageDialog(this, "You moved into an enemy and defeated it! +50 score.");
                } else {
                    // player fails to instantly kill: enemy hits player (full damage) and stays or may vanish based on random (we'll let it stay)
                    player.reduceHp(enemyFound.getDamage());
                    addHighlight(enemyFound.getPosition());
                    JOptionPane.showMessageDialog(this, "You failed to defeat the enemy. It hit you for " + enemyFound.getDamage() + " damage! HP: " + player.getHp());
                    if (!player.isAlive()) {
                        // if died, remove the enemy that finished him and exit
                        world.removeEnemy(enemyFound);
                        handler.handle("playerDied", enemyFound);
                        return;
                    }
                    // enemy remains (no vanish) so player may have to fight later
                }
            } else {
                // -------------------------------
                // 2) Adjacent enemies may attack probabilistically (kamikaze-like if they attack)
                // -------------------------------
                java.util.List<Enemy> adjacent = world.getAdjacentEnemies(ppos.x, ppos.y);
                if (!adjacent.isEmpty()) {
                    for (Enemy ae : new ArrayList<>(adjacent)) {
                        if (Math.random() < ADJACENT_ATTACK_PROB) {
                            // attack occurs: damage and enemy vanishes
                            player.reduceHp(ae.getDamage());
                            addHighlight(ae.getPosition());
                            world.removeEnemy(ae);
                            JOptionPane.showMessageDialog(this, ae.getType() + " attacked you for " + ae.getDamage() + " damage!");
                            if (!player.isAlive()) {
                                JOptionPane.showMessageDialog(this, "You died from the attack. Game over.");
                                handler.handle("playerDied", ae);
                                return;
                            }
                        } else {
                            // no attack this turn; leave enemy in place
                            // optional: small message suppressed for less spam
                        }
                    }
                }

                // -------------------------------
                // 3) Move remaining enemies one orthogonal step
                // -------------------------------
                world.moveEnemiesTowardsOrthogonal(ppos);

                // -------------------------------
                // 4) After movement, if enemy moved onto player -> fight (existing probabilistic fight)
                // -------------------------------
                Optional<Enemy> meet = world.peekEnemyAt(ppos.x, ppos.y);
                if (meet.isPresent()) {
                    Enemy mover = meet.get();
                    boolean won = player.fight(mover);
                    addHighlight(mover.getPosition());
                    if (won) {
                        world.removeEnemy(mover);
                        JOptionPane.showMessageDialog(this, "An enemy moved onto you and you defeated it!");
                    } else {
                        if (!player.isAlive()) {
                            world.removeEnemy(mover);
                            JOptionPane.showMessageDialog(this, "An enemy moved onto you and killed you. Game over.");
                            handler.handle("playerDied", mover);
                            return;
                        } else {
                            JOptionPane.showMessageDialog(this, "An enemy moved onto you and hit you. HP now: " + player.getHp());
                        }
                    }
                }
            }

            // -------------------------------
            // 5) Collect booster if present
            // -------------------------------
            if (world.isBoosterAt(player.getPosition().x, player.getPosition().y)) {
                String b = world.collectBoosterAt(player.getPosition().x, player.getPosition().y);
                player.addToInventory(b);
                handler.handle("collectedBooster", b);
            }

            // -------------------------------
            // 6) Check destination
            // -------------------------------
            if (world.isDestination(player.getPosition().x, player.getPosition().y)) {
                handler.handle("reachedDestination", null);
                return;
            }

            handler.handle("move", null);
            repaint();
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}

// -------------------- PLAYER CLASS --------------------
class Player {
    private final String name;
    private int hp = 100;
    private int score = 0;
    private final java.util.List<String> inventory = new ArrayList<>();
    private final Set<String> uniqueItems = new HashSet<>();
    private Point position = new Point(0, 0);
    private int steps = 0;
    private int flags = 0;
    private final boolean[][] visited;

    public Player(String name) {
        this.name = name;
        inventory.add("Basic Sword");
        inventory.add("Health Potion");
        visited = new boolean[30][30];
        visited[0][0] = true;
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getScore() { return score; }
    public int getSteps() { return steps; }
    public java.util.List<String> getInventory() { return Collections.unmodifiableList(inventory); }

    public void addToInventory(String item) {
        inventory.add(item);
        uniqueItems.add(item);
        if (item.toLowerCase().contains("shield")) flags |= 1;
    }

    public boolean hasVisited(int x, int y) {
        if (x < 0 || y < 0 || x >= visited.length || y >= visited[0].length) return false;
        return visited[x][y];
    }

    public String getVisitedString() {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < visited.length; x++) for (int y = 0; y < visited[0].length; y++)
            if (visited[x][y]) sb.append("(").append(x).append("-").append(y).append(");");
        return sb.toString();
    }

    public boolean isAlive() { return hp > 0; }

    // orthogonal one-step movement only (dx or dy should be -1,0,1 with one non-zero)
    public boolean move(int dx, int dy, int cols, int rows) {
        if (Math.abs(dx) + Math.abs(dy) != 1) return false; // disallow diagonal / zero-step
        int nx = position.x + dx;
        int ny = position.y + dy;
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) {
            Toolkit.getDefaultToolkit().beep();
            return false;
        } else {
            position.setLocation(nx, ny);
            steps++;
            if (nx < visited.length && ny < visited[0].length) visited[nx][ny] = true;
            if ((steps % 5) == 0) addScore(5);
            return true;
        }
    }

    public Point getPosition() { return new Point(position); }

    // existing fight: random-based outcome using enemy.getPower()
    public boolean fight(Enemy enemy) {
        Random r = new Random();
        int p = r.nextInt(100);
        int effectivePower = enemy.getPower();
        if ((flags & 1) == 1) effectivePower = Math.max(0, enemy.getPower() - 15);
        if (p < effectivePower) {
            hp -= enemy.getDamage();
            return false;
        } else {
            addScore(50);
            addToInventory(enemy.getDropItem());
            return true;
        }
    }

    // used by adjacent attacks where enemy deals fixed damage (no randomness)
    public void reduceHp(int dmg) { hp -= dmg; }

    public void addScore(int points) { score += points; }
}

// -------------------- GAME WORLD --------------------
class GameWorld {
    private final int cols;
    private final int rows;
    private final String[][] map;
    private final java.util.List<Enemy> enemies = new ArrayList<>();
    private final java.util.List<Point> boosters = new ArrayList<>();
    private Point destination;
    private final String[] treasurePool = {"Silver Shield", "Bronze Key", "Gold Coin", "Speed Boots", "Ancient Scroll"};
    private final Random rand = new Random();

    public GameWorld(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        map = new String[cols][rows];
        for (int x = 0; x < cols; x++) for (int y = 0; y < rows; y++)
            map[x][y] = ((x + y) % 7 == 0) ? "Forest" : "Plain";

        spawnEnemies(10);
        scatterBoosters(4);
        placeDestination();
    }

    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public java.util.List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }

    private void spawnEnemies(int count) {
        Set<String> used = new HashSet<>();
        while (enemies.size() < count) {
            int x = rand.nextInt(cols);
            int y = rand.nextInt(rows);
            String key = x + "," + y;
            if (used.contains(key) || (x == 0 && y == 0)) continue;
            used.add(key);
            int type = rand.nextInt(100);
            Enemy enemy = (type < 60) ? new Goblin(x, y) : (type < 90) ? new Orc(x, y) : new Dragon(x, y);
            enemies.add(enemy);
        }
    }

    private void scatterBoosters(int bcount) {
        Set<String> used = new HashSet<>();
        while (boosters.size() < bcount) {
            int x = rand.nextInt(cols);
            int y = rand.nextInt(rows);
            String key = x + "," + y;
            if (used.contains(key) || (x == 0 && y == 0)) continue;
            used.add(key);
            boosters.add(new Point(x, y));
        }
    }

    private void placeDestination() {
        destination = new Point(cols - 1, rows - 1);
    }

    public boolean isBoosterAt(int x, int y) {
        for (Point p : boosters) if (p.x == x && p.y == y) return true;
        return false;
    }

    public String collectBoosterAt(int x, int y) {
        Iterator<Point> it = boosters.iterator();
        while (it.hasNext()) {
            Point p = it.next();
            if (p.x == x && p.y == y) { it.remove(); return "Booster-" + x + "-" + y; }
        }
        return null;
    }

    public boolean isDestination(int x, int y) { return destination.x == x && destination.y == y; }

    // Return a copy list of enemies adjacent (N/S/E/W) to cell (x,y)
    public java.util.List<Enemy> getAdjacentEnemies(int x, int y) {
        java.util.List<Enemy> res = new ArrayList<>();
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1];
            for (Enemy enemy : enemies) {
                if (enemy.getPosition().x == nx && enemy.getPosition().y == ny) {
                    res.add(enemy);
                }
            }
        }
        return res;
    }

    // Move enemies exactly one orthogonal step (no diagonal). Each enemy tries to move closer to the player by
    // choosing one axis (horizontal or vertical) where a single step reduces Manhattan distance.
    // If the preferred axis is blocked by another enemy, it tries the other axis; otherwise stays.
    public void moveEnemiesTowardsOrthogonal(Point playerPos) {
        java.util.List<Enemy> snapshot = new ArrayList<>(enemies);
        Set<String> occupied = new HashSet<>();
        for (Enemy enemy : snapshot) occupied.add(enemy.getPosition().x + "," + enemy.getPosition().y);

        for (Enemy enemy : snapshot) {
            int ex = enemy.getPosition().x;
            int ey = enemy.getPosition().y;
            int dx = Integer.compare(playerPos.x, ex); // -1,0,1
            int dy = Integer.compare(playerPos.y, ey); // -1,0,1
            int distX = Math.abs(playerPos.x - ex);
            int distY = Math.abs(playerPos.y - ey);

            boolean moved = false;
            java.util.List<int[]> tryMoves = new ArrayList<>();

            if (distX >= distY) {
                if (dx != 0) tryMoves.add(new int[]{ex + dx, ey});
                if (dy != 0) tryMoves.add(new int[]{ex, ey + dy});
            } else {
                if (dy != 0) tryMoves.add(new int[]{ex, ey + dy});
                if (dx != 0) tryMoves.add(new int[]{ex + dx, ey});
            }

            for (int[] mv : tryMoves) {
                int nx = mv[0], ny = mv[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue;
                String key = nx + "," + ny;
                if (occupied.contains(key)) continue;
                enemy.setPosition(nx, ny);
                occupied.add(key);
                moved = true;
                break;
            }
            // stay if cannot move
        }
    }

    public Optional<Enemy> peekEnemyAt(int x, int y) {
        for (Enemy enemy : enemies) if (enemy.getPosition().x == x && enemy.getPosition().y == y) return Optional.of(enemy);
        return Optional.empty();
    }

    public void removeEnemy(Enemy e) { enemies.remove(e); }

    // When booster collected, kill one enemy (last in list)
    public boolean killOneEnemy() {
        if (enemies.isEmpty()) return false;
        enemies.remove(enemies.size() - 1);
        return true;
    }

    public String generateTreasure() { return treasurePool[rand.nextInt(treasurePool.length)]; }
}

// -------------------- ENEMY HIERARCHY --------------------
abstract class Enemy {
    protected final String type;
    protected final int power;
    protected final int damage;
    protected Point position;
    protected final String dropItem;

    public Enemy(String type, int power, int damage, int x, int y, String dropItem) {
        this.type = type;
        this.power = power;
        this.damage = damage;
        this.position = new Point(x, y);
        this.dropItem = dropItem;
    }

    public String getType() { return type; }
    public int getPower() { return power; }
    public int getDamage() { return damage; }
    public Point getPosition() { return new Point(position); }
    public String getDropItem() { return dropItem; }
    public void setPosition(int x, int y) { this.position.setLocation(x, y); }
    public abstract String description();
}

class Goblin extends Enemy {
    public Goblin(int x, int y) { super("Goblin", 35, 10, x, y, "Goblin Tooth"); }
    @Override public String description() { return "Sneaky and weak creature."; }
}

class Orc extends Enemy {
    public Orc(int x, int y) { super("Orc", 55, 20, x, y, "Orc Axe"); }
    @Override public String description() { return "Strong and tough enemy."; }
}

class Dragon extends Enemy {
    public Dragon(int x, int y) { super("Dragon", 80, 40, x, y, "Dragon Scale"); }
    @Override public String description() { return "Huge and powerful mythical beast."; }
}
