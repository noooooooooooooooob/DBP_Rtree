package org.dfpl.dbp.rtree;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class RTreeImpl extends JPanel implements RTree {

    private Node root;
    private final int M = 4;
    private JFrame frame;

    private Rectangle searchRange;
    private Point searchSource;
    private List<Point> searchResultPoints;

    private String currentActionInfo = "";
    private final Object lock = new Object();
    private boolean isWaiting = false;

    private static final int SCALE = 3;
    private static final int OFFSET_X = 50;
    private static final int OFFSET_Y = 50;
    private static final int PANEL_WIDTH = 800;
    private static final int PANEL_HEIGHT = 800;

    private static final Color COLOR_LEAF_MBR = new Color(255, 165, 0, 100);
    private static final Color COLOR_NODE_MBR = new Color(100, 100, 100, 50);
    private static final Color COLOR_VISITED = new Color(0, 0, 255, 50);
    private static final Color COLOR_PRUNED = new Color(200, 200, 200, 50);
    private static final Color COLOR_HIGHLIGHT = new Color(0, 255, 0, 100);
    private static final Color COLOR_SEARCH_RANGE = new Color(255, 0, 0, 30);
    private static final int DELAY_MS = 50;

    private class Node {
        List<Object> children;
        boolean isLeaf;
        Rectangle mbr;
        Node parent;
        Color drawColor;

        Node(boolean isLeaf) {
            this.isLeaf = isLeaf;
            this.children = new ArrayList<>();
            this.drawColor = isLeaf ? COLOR_LEAF_MBR : COLOR_NODE_MBR;
        }

        void updateMBR() {
            if (children.isEmpty()) return;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

            for (Object child : children) {
                double lx, ly, rx, ry;
                if (isLeaf) {
                    Point p = (Point) child;
                    lx = rx = p.getX();
                    ly = ry = p.getY();
                } else {
                    Node n = (Node) child;
                    lx = n.mbr.getLeftTop().getX();
                    ly = n.mbr.getLeftTop().getY();
                    rx = n.mbr.getRightBottom().getX();
                    ry = n.mbr.getRightBottom().getY();
                }
                if (lx < minX) minX = lx;
                if (ly < minY) minY = ly;
                if (rx > maxX) maxX = rx;
                if (ry > maxY) maxY = ry;
            }
            this.mbr = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
        }
    }

    public RTreeImpl() {
        frame = new JFrame("R-Tree Visualization (Press 'e' to continue)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(this);
        frame.setSize(PANEL_WIDTH + 100, PANEL_HEIGHT + 100);
        frame.setVisible(true);

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == 'e' || e.getKeyChar() == 'E') {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }
        });
        frame.setFocusable(true);
        frame.requestFocusInWindow();

        searchResultPoints = new ArrayList<>();
    }

    private void waitForKey() {
        isWaiting = true;
        repaint();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isWaiting = false;
        repaint();
    }

    private void refresh(boolean resetColors) {
        if (resetColors) resetNodeColors(root);
        repaint();
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void resetNodeColors(Node node) {
        if (node == null) return;
        node.drawColor = node.isLeaf ? COLOR_LEAF_MBR : COLOR_NODE_MBR;
        if (!node.isLeaf) {
            for (Object child : node.children) {
                if (child instanceof Node) resetNodeColors((Node) child);
            }
        }
    }

    private int toScreenX(double x) {
        return (int) (x * SCALE) + OFFSET_X;
    }

    private int toScreenY(double y) {
        return PANEL_HEIGHT - ((int) (y * SCALE) + OFFSET_Y);
    }

    private int toScreenLength(double len) {
        return (int) (len * SCALE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString(currentActionInfo, 20, 30);
        if (isWaiting) {
            g2.setColor(Color.RED);
            g2.drawString("Press 'e' to continue...", 20, 55);
        }

        if (root != null) drawNode(g2, root);

        if (searchRange != null) {
            int x = toScreenX(searchRange.getLeftTop().getX());
            int y = toScreenY(searchRange.getRightBottom().getY());
            int w = toScreenLength(searchRange.getRightBottom().getX() - searchRange.getLeftTop().getX());
            int h = toScreenLength(searchRange.getRightBottom().getY() - searchRange.getLeftTop().getY());

            g2.setColor(COLOR_SEARCH_RANGE);
            g2.fillRect(x, y, w, h);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x, y, w, h);
        }

        if (searchSource != null) {
            int x = toScreenX(searchSource.getX());
            int y = toScreenY(searchSource.getY());
            g2.setColor(Color.RED);
            g2.fillOval(x - 5, y - 5, 10, 10);
            g2.drawString("Source", x + 10, y);
        }

        if (searchResultPoints != null) {
            g2.setColor(Color.MAGENTA);
            for (Point p : searchResultPoints) {
                int x = toScreenX(p.getX());
                int y = toScreenY(p.getY());
                g2.fillOval(x - 4, y - 4, 8, 8);
            }
        }
    }

    private void drawNode(Graphics2D g2, Node node) {
        if (node.mbr != null) {
            double minX = node.mbr.getLeftTop().getX();
            double minY = node.mbr.getLeftTop().getY();
            double maxX = node.mbr.getRightBottom().getX();
            double maxY = node.mbr.getRightBottom().getY();

            int sx = toScreenX(minX);
            int sy = toScreenY(maxY);
            int sw = toScreenLength(maxX - minX);
            int sh = toScreenLength(maxY - minY);

            g2.setColor(node.drawColor);
            g2.fillRect(sx, sy, sw, sh);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1));
            g2.drawRect(sx, sy, sw, sh);
        }

        if (node.isLeaf) {
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (Object obj : node.children) {
                Point p = (Point) obj;
                int px = toScreenX(p.getX());
                int py = toScreenY(p.getY());
                g2.fillOval(px - 3, py - 3, 6, 6);
                g2.drawString(String.format("(%.0f,%.0f)", p.getX(), p.getY()), px + 6, py);
            }
        } else {
            for (Object obj : node.children) {
                drawNode(g2, (Node) obj);
            }
        }
    }

    @Override
    public void add(Point point) {
        currentActionInfo = "Inserting: (" + (int)point.getX() + ", " + (int)point.getY() + ")";
        searchRange = null;
        searchSource = null;
        searchResultPoints.clear();
        resetNodeColors(root);

        if (root == null) {
            root = new Node(true);
            root.children.add(point);
            root.updateMBR();
        } else {
            Node leaf = chooseLeaf(root, point);

            Node curr = leaf;
            while(curr != null) {
                curr.drawColor = COLOR_HIGHLIGHT;
                curr = curr.parent;
            }
            refresh(false);

            leaf.children.add(point);
            leaf.updateMBR();

            Node p = leaf.parent;
            while(p != null) {
                p.updateMBR();
                p = p.parent;
            }

            if (leaf.children.size() > M) {
                split(leaf);
            } else {
                adjustTree(leaf);
            }
        }
        refresh(true);
        waitForKey();
    }

    private Node chooseLeaf(Node node, Point point) {
        if (node.isLeaf) return node;
        Node best = null;
        double minEnl = Double.MAX_VALUE;

        for (Object obj : node.children) {
            Node child = (Node) obj;
            double areaBefore = getArea(child.mbr);
            double areaAfter = getArea(union(child.mbr, point));
            double enl = areaAfter - areaBefore;
            if (enl < minEnl) {
                minEnl = enl;
                best = child;
            }
        }
        return chooseLeaf(best, point);
    }

    private void split(Node node) {
        Node newNode = new Node(node.isLeaf);
        newNode.parent = node.parent;
        List<Object> entries = new ArrayList<>(node.children);
        node.children.clear();

        if (node.isLeaf) {
            entries.sort((o1, o2) -> Double.compare(((Point)o1).getX(), ((Point)o2).getX()));
        } else {
            entries.sort((o1, o2) -> Double.compare(((Node)o1).mbr.getLeftTop().getX(), ((Node)o2).mbr.getLeftTop().getX()));
        }

        int mid = entries.size() / 2;
        node.children.addAll(entries.subList(0, mid));
        newNode.children.addAll(entries.subList(mid, entries.size()));

        node.updateMBR();
        newNode.updateMBR();

        if (node == root) {
            Node newRoot = new Node(false);
            newRoot.children.add(node);
            newRoot.children.add(newNode);
            node.parent = newRoot;
            newNode.parent = newRoot;
            newRoot.updateMBR();
            root = newRoot;
        } else {
            Node parent = node.parent;
            parent.children.add(newNode);
            parent.updateMBR();
            if (parent.children.size() > M) {
                split(parent);
            } else {
                adjustTree(parent);
            }
        }
    }

    private void adjustTree(Node node) {
        while (node != null) {
            node.updateMBR();
            node = node.parent;
        }
    }

    private double getArea(Rectangle r) {
        return (r.getRightBottom().getX() - r.getLeftTop().getX()) * (r.getRightBottom().getY() - r.getLeftTop().getY());
    }

    private Rectangle union(Rectangle r, Point p) {
        double minX = Math.min(r.getLeftTop().getX(), p.getX());
        double minY = Math.min(r.getLeftTop().getY(), p.getY());
        double maxX = Math.max(r.getRightBottom().getX(), p.getX());
        double maxY = Math.max(r.getRightBottom().getY(), p.getY());
        return new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        currentActionInfo = "Ready to Search: Range " + rectangle;
        searchRange = rectangle;
        searchSource = null;
        searchResultPoints.clear();
        resetNodeColors(root);
        refresh(false);

        waitForKey();

        currentActionInfo = "Searching...";
        List<Point> results = new ArrayList<>();
        if (root != null) searchRec(root, rectangle, results);

        searchResultPoints.addAll(results);
        refresh(true);
        return results.iterator();
    }

    private void searchRec(Node node, Rectangle range, List<Point> results) {
        if (node == null) return;

        if (!intersects(node.mbr, range)) {
            node.drawColor = COLOR_PRUNED;
            refresh(false);
            return;
        }

        node.drawColor = COLOR_VISITED;
        refresh(false);

        if (node.isLeaf) {
            for (Object obj : node.children) {
                Point p = (Point) obj;
                if (contains(range, p)) {
                    results.add(p);
                }
            }
        } else {
            for (Object obj : node.children) {
                searchRec((Node) obj, range, results);
            }
        }
    }

    private boolean intersects(Rectangle r1, Rectangle r2) {
        return !(r2.getLeftTop().getX() > r1.getRightBottom().getX() ||
                r2.getRightBottom().getX() < r1.getLeftTop().getX() ||
                r2.getLeftTop().getY() > r1.getRightBottom().getY() ||
                r2.getRightBottom().getY() < r1.getLeftTop().getY());
    }

    private boolean contains(Rectangle r, Point p) {
        return p.getX() >= r.getLeftTop().getX() && p.getX() <= r.getRightBottom().getX() &&
                p.getY() >= r.getLeftTop().getY() && p.getY() <= r.getRightBottom().getY();
    }

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        currentActionInfo = "Ready to KNN: Source (" + (int)source.getX() + ", " + (int)source.getY() + ")";
        searchRange = null;
        searchSource = source;
        searchResultPoints.clear();
        resetNodeColors(root);
        refresh(false);

        waitForKey();

        currentActionInfo = "KNN Searching...";
        if (root == null) return Collections.emptyIterator();

        PriorityQueue<Object> pq = new PriorityQueue<>((o1, o2) -> {
            double d1 = (o1 instanceof Point) ? source.distance((Point) o1) : minDist(source, ((Node) o1).mbr);
            double d2 = (o2 instanceof Point) ? source.distance((Point) o2) : minDist(source, ((Node) o2).mbr);
            return Double.compare(d1, d2);
        });

        pq.add(root);
        List<Point> results = new ArrayList<>();

        while (!pq.isEmpty() && results.size() < maxCount) {
            Object curr = pq.poll();
            resetNodeColors(root);

            if (curr instanceof Point) {
                Point p = (Point) curr;
                results.add(p);
                searchResultPoints.add(p);
                refresh(false);
            } else {
                Node node = (Node) curr;
                node.drawColor = COLOR_VISITED;
                pq.addAll(node.children);
                refresh(false);
            }
        }
        refresh(true);
        return results.iterator();
    }

    private double minDist(Point p, Rectangle r) {
        double dx = Math.max(Math.max(r.getLeftTop().getX() - p.getX(), 0), p.getX() - r.getRightBottom().getX());
        double dy = Math.max(Math.max(r.getLeftTop().getY() - p.getY(), 0), p.getY() - r.getRightBottom().getY());
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public void delete(Point point) {
        currentActionInfo = "Deleting: (" + (int)point.getX() + ", " + (int)point.getY() + ")";
        searchRange = null;
        searchSource = null;
        searchResultPoints.clear();
        resetNodeColors(root);

        if (root != null) {
            if (deleteRec(root, point)) {
                if (root.children.isEmpty()) root = null;
                else if (!root.isLeaf && root.children.size() == 1) {
                    root = (Node) root.children.get(0);
                    root.parent = null;
                }
            }
        }
        refresh(true);
        waitForKey();
    }

    private boolean deleteRec(Node node, Point p) {
        if (node.isLeaf) {
            for (int i = 0; i < node.children.size(); i++) {
                Point lp = (Point) node.children.get(i);
                if (lp.getX() == p.getX() && lp.getY() == p.getY()) {
                    node.drawColor = COLOR_HIGHLIGHT;
                    refresh(false);

                    node.children.remove(i);
                    node.updateMBR();
                    return true;
                }
            }
            return false;
        } else {
            for (Object obj : node.children) {
                Node child = (Node) obj;
                if (contains(child.mbr, p)) {
                    if (deleteRec(child, p)) {
                        if (child.children.isEmpty()) {
                            node.children.remove(child);
                        }
                        node.drawColor = COLOR_HIGHLIGHT;
                        node.updateMBR();
                        refresh(false);
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }
}