package org.dfpl.dbp.rtree.team2;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class RTreeImpl implements RTree {

    // ---------- R-Tree parameters ----------
    private static final int MAX_ENTRIES = 4; // M
    private static final int MIN_ENTRIES = 2; // m

    // ---------- geometry helpers (Rectangle ← leftTop/rightBottom 버전 대응) ----------

    private static double xMin(Rectangle r) {
        return Math.min(r.getLeftTop().getX(), r.getRightBottom().getX());
    }

    private static double xMax(Rectangle r) {
        return Math.max(r.getLeftTop().getX(), r.getRightBottom().getX());
    }

    private static double yMin(Rectangle r) {
        return Math.min(r.getLeftTop().getY(), r.getRightBottom().getY());
    }

    private static double yMax(Rectangle r) {
        return Math.max(r.getLeftTop().getY(), r.getRightBottom().getY());
    }

    private static double area(Rectangle r) {
        double w = xMax(r) - xMin(r);
        double h = yMax(r) - yMin(r);
        if (w < 0) w = 0;
        if (h < 0) h = 0;
        return w * h;
    }

    private static Rectangle rectFrom(Point p) {
        return new Rectangle(new Point(p.getX(), p.getY()),
                new Point(p.getX(), p.getY()));
    }

    private static Rectangle copyRect(Rectangle r) {
        return new Rectangle(
                new Point(r.getLeftTop().getX(), r.getLeftTop().getY()),
                new Point(r.getRightBottom().getX(), r.getRightBottom().getY())
        );
    }

    private static Rectangle unionRect(Rectangle a, Rectangle b) {
        double nx1 = Math.min(xMin(a), xMin(b));
        double ny1 = Math.min(yMin(a), yMin(b));
        double nx2 = Math.max(xMax(a), xMax(b));
        double ny2 = Math.max(yMax(a), yMax(b));
        return new Rectangle(new Point(nx1, ny1), new Point(nx2, ny2));
    }

    private static double enlargement(Rectangle a, Rectangle b) {
        double oldArea = area(a);
        Rectangle u = unionRect(a, b);
        double newArea = area(u);
        return newArea - oldArea;
    }

    private static boolean intersects(Rectangle a, Rectangle b) {
        if (xMax(a) < xMin(b) || xMin(a) > xMax(b)) return false;
        if (yMax(a) < yMin(b) || yMin(a) > yMax(b)) return false;
        return true;
    }

    private static boolean rectContains(Rectangle r, Point p) {
        double x = p.getX();
        double y = p.getY();
        return x >= xMin(r) && x <= xMax(r) &&
                y >= yMin(r) && y <= yMax(r);
    }

    // 사각형과 점 사이의 최소 거리 (kNN에서 사용)
    private static double mindist(Rectangle r, Point p) {
        double x = p.getX();
        double y = p.getY();
        double dx = (x < xMin(r)) ? xMin(r) - x : (x > xMax(r) ? x - xMax(r) : 0);
        double dy = (y < yMin(r)) ? yMin(r) - y : (y > yMax(r) ? y - yMax(r) : 0);
        return Math.hypot(dx, dy);
    }

    private static boolean same(Point a, Point b) {
        return Double.compare(a.getX(), b.getX()) == 0 &&
                Double.compare(a.getY(), b.getY()) == 0;
    }

    // ---------- Tree structures ----------

    private static final AtomicLong NODE_IDS = new AtomicLong(1);

    private static class Entry {
        Rectangle mbr;
        Node child;      // null이면 leaf 엔트리
        Point userPoint; // leaf에서만 사용

        Entry(Rectangle m, Node c) { mbr = m; child = c; }
        Entry(Rectangle m, Point p) { mbr = m; userPoint = p; }
    }

    private static class Node {
        final long id = NODE_IDS.getAndIncrement();
        boolean isLeaf;
        ArrayList<Entry> entries = new ArrayList<>();
        Node parent;
        Rectangle mbr; // 이 노드를 커버하는 MBR

        Node(boolean isLeaf) { this.isLeaf = isLeaf; }

        void recompute() {
            if (entries.isEmpty()) {
                mbr = null;
                return;
            }
            Rectangle agg = copyRect(entries.get(0).mbr);
            for (int i = 1; i < entries.size(); i++) {
                agg = unionRect(agg, entries.get(i).mbr);
            }
            mbr = agg;
        }
    }

    private Node root = new Node(true);
    private int size = 0;

    // ---------- Visualization ----------

    private static class VisualFrame extends JFrame {
        VisualFrame() {
            super("R-Tree Visualizer (4-way)");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(820, 820);
            setLocationByPlatform(true);
        }
    }

    private class Visual extends JPanel {
        private final VisualFrame frame;

        // 스냅샷용 노드 (실제 트리와 분리)
        private class SNode {
            long id;
            boolean isLeaf;
            Rectangle mbr;
            java.util.List<SNode> children = new ArrayList<>();
            java.util.List<Point> points  = new ArrayList<>(); // leaf points
        }

        private SNode snapRoot;

        // 월드 좌표 범위 (자동 스케일링용)
        double wx1 = 0, wy1 = 0, wx2 = 200, wy2 = 200;

        // 마킹용
        Rectangle lastQuery;
        Point lastSource;
        Point lastInserted, lastDeleted;
        java.util.Set<Long> visited = Collections.synchronizedSet(new HashSet<>());
        java.util.Set<Long> pruned  = Collections.synchronizedSet(new HashSet<>());
        java.util.List<Point> knnSoFar = new ArrayList<>();

        // 스텝 진행용 플래그
        private volatile boolean stepRequested = false;

        Visual() {
            frame = new VisualFrame();
            frame.setContentPane(this);

            // 키 입력으로 스텝 진행
            frame.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    stepRequested = true;
                }
            });

            frame.setFocusable(true);
            frame.setVisible(true);
            frame.requestFocus();
        }

        void resetMarks() {
            lastQuery = null;
            lastSource = null;
            lastInserted = null;
            lastDeleted = null;
            visited.clear();
            pruned.clear();
            knnSoFar = new ArrayList<>();
        }

        private SNode snap(Node n) {
            if (n == null) return null;
            SNode s = new SNode();
            s.id = n.id;
            s.isLeaf = n.isLeaf;
            s.mbr = (n.mbr == null) ? null : copyRect(n.mbr);
            if (n.isLeaf) {
                for (Entry e : n.entries) {
                    if (e.userPoint != null) {
                        s.points.add(new Point(e.userPoint.getX(), e.userPoint.getY()));
                    }
                }
            } else {
                for (Entry e : n.entries) {
                    if (e.child != null) {
                        SNode childSnap = snap(e.child);
                        if (childSnap != null) {
                            s.children.add(childSnap);
                        }
                    }
                }
            }
            return s;
        }

        void update(Node r) {
            this.snapRoot = snap(r);

            repaint();
        }


        private void waitForStep() {
            stepRequested = false;
            while (!stepRequested) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}
            }
        }

        void redrawStep() {
            repaint();
            waitForStep();
        }

        void setQuery(Rectangle q) { lastQuery = q; }
        void setSource(Point s) { lastSource = s; }
        void setLastInserted(Point p) { lastInserted = p; }
        void setLastDeleted(Point p) { lastDeleted = p; }
        void setKnn(java.util.List<Point> l) { knnSoFar = new ArrayList<>(l); }
        void markVisited(Node n) { if (n != null) visited.add(n.id); }
        void markPruned(Node n) { if (n != null) pruned.add(n.id); }

        void showPath(java.util.List<Node> path) {
            if (path == null || path.isEmpty()) return;
            for (Node n : path) {
                markVisited(n);
                redrawStep();
            }
        }

        void flashSplit(Node left, Node right) {
            if (left == null && right == null) return;
            for (int i = 0; i < 2; i++) {
                if (left != null) markVisited(left);
                if (right != null) markVisited(right);
                redrawStep();
                if (left != null) pruned.add(left.id);
                if (right != null) pruned.add(right.id);
                redrawStep();
                if (left != null) { visited.remove(left.id); pruned.remove(left.id); }
                if (right != null) { visited.remove(right.id); pruned.remove(right.id); }
            }
        }

        void flashUnderflow(Node n) {
            if (n == null) return;
            for (int i = 0; i < 2; i++) {
                markPruned(n);
                redrawStep();
                pruned.remove(n.id);
                redrawStep();
            }
        }

        void flashReinsert(Point p) {
            setLastInserted(p);
            redrawStep();
            setLastInserted(null);
            redrawStep();
        }

        private void autoFitSnapshot(SNode r) {
            if (r == null || r.mbr == null) {
                wx1 = 0; wy1 = 0; wx2 = 200; wy2 = 200;
                return;
            }
            wx1 = xMin(r.mbr);
            wy1 = yMin(r.mbr);
            wx2 = xMax(r.mbr);
            wy2 = yMax(r.mbr);
            double dx = (wx2 - wx1) * 0.05 + 1;
            double dy = (wy2 - wy1) * 0.05 + 1;
            wx1 -= dx; wy1 -= dy; wx2 += dx; wy2 += dy;
        }

        private java.awt.Point map(double x, double y, int W, int H) {
            double nx = (x - wx1) / Math.max(1e-9, (wx2 - wx1));
            double ny = (y - wy1) / Math.max(1e-9, (wy2 - wy1));
            int sx = (int) Math.round(40 + nx * (W - 80));
            int sy = (int) Math.round(H - 40 - ny * (H - 80)); // 위쪽이 +Y
            return new java.awt.Point(sx, sy);
        }

        private java.awt.Rectangle toAwtRect(Rectangle r, int W, int H) {
            java.awt.Point a = map(xMin(r), yMin(r), W, H);
            java.awt.Point b = map(xMax(r), yMax(r), W, H);
            int x = Math.min(a.x, b.x);
            int y = Math.min(a.y, b.y);
            int w = Math.abs(a.x - b.x);
            int h = Math.abs(a.y - b.y);
            return new java.awt.Rectangle(x, y, w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int W = getWidth();
            int H = getHeight();

            g2.setColor(Color.white);
            g2.fillRect(0, 0, W, H);
            g2.setColor(Color.lightGray);
            g2.drawRect(20, 20, W - 40, H - 40);

            if (snapRoot != null) {
                drawNode(g2, snapRoot, 0, W, H);
            }

            // 검색 박스 표시
            if (lastQuery != null) {
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(new Color(220, 0, 0, 180));
                java.awt.Rectangle rr = toAwtRect(lastQuery, W, H);
                g2.draw(rr);
                g2.setStroke(new BasicStroke(1f));
            }

            // kNN 소스 점
            if (lastSource != null) {
                java.awt.Point ps = map(lastSource.getX(), lastSource.getY(), W, H);
                g2.setColor(new Color(0, 120, 255));
                int r = 6;
                g2.fillOval(ps.x - r, ps.y - r, 2 * r, 2 * r);
                g2.drawString("source", ps.x + 8, ps.y - 8);
            }

            // 마지막 삽입/삭제 포인트 표시
            if (lastInserted != null) {
                java.awt.Point sp = map(lastInserted.getX(), lastInserted.getY(), W, H);
                g2.setColor(new Color(0, 170, 0));
                int r = 5;
                g2.fillOval(sp.x - r, sp.y - r, 2 * r, 2 * r);

                String label = String.format("inserted (%.1f, %.1f)",
                        lastInserted.getX(), lastInserted.getY());
                g2.drawString(label, sp.x + 8, sp.y - 8);
            }

            if (lastDeleted != null) {
                java.awt.Point sp = map(lastDeleted.getX(), lastDeleted.getY(), W, H);
                g2.setColor(new Color(190, 0, 190));
                int r = 5;
                g2.fillOval(sp.x - r, sp.y - r, 2 * r, 2 * r);

                String label = String.format("deleted (%.1f, %.1f)",
                        lastDeleted.getX(), lastDeleted.getY());
                g2.drawString(label, sp.x + 8, sp.y - 8);
            }

            // kNN 결과 포인트 강조 + 좌표 라벨 표시
            if (!knnSoFar.isEmpty()) {
                g2.setColor(new Color(255, 0, 0));

                int idx = 1; // kNN 순서 표시 (1st, 2nd, 3rd)
                for (Point p : knnSoFar) {
                    java.awt.Point sp = map(p.getX(), p.getY(), W, H);
                    int r = 5;

                    // 점 찍기
                    g2.fillOval(sp.x - r, sp.y - r, 2 * r, 2 * r);

                    // label: k(#) (x,y)
                    String label = String.format("k%d (%.1f, %.1f)", idx, p.getX(), p.getY());
                    g2.drawString(label, sp.x + 6, sp.y - 6);

                    idx++;
                }
            }
        }

        private void drawNode(Graphics2D g2, SNode n, int depth, int W, int H) {
            Color[] pal = {
                    new Color(0, 0, 0, 30),
                    new Color(0, 120, 255, 40),
                    new Color(0, 170, 0, 40),
                    new Color(255, 140, 0, 40),
                    new Color(190, 0, 190, 40)
            };
            Color fill = pal[depth % pal.length];

            if (n.mbr != null) {
                java.awt.Rectangle rr = toAwtRect(n.mbr, W, H);
                g2.setColor(fill);
                g2.fill(rr);

                if (pruned.contains(n.id)) g2.setColor(new Color(160, 160, 160));
                else if (visited.contains(n.id)) g2.setColor(Color.red);
                else g2.setColor(Color.darkGray);

                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(rr);
            }

            if (n.isLeaf) {
                g2.setColor(Color.black);
                for (Point p : n.points) {
                    java.awt.Point sp = map(p.getX(), p.getY(), W, H);
                    int d = 4;
                    g2.fillOval(sp.x - d, sp.y - d, 2 * d, 2 * d);
                }
            } else {
                for (SNode ch : n.children) {
                    drawNode(g2, ch, depth + 1, W, H);
                }
            }
        }
    }

    private final Visual visual = new Visual();

    // ---------- ctor ----------
    public RTreeImpl() {
        visual.update(root); // empty tree 표시
    }

    // ---------- Public API 구현 ----------

    @Override
    public void add(Point point) {
        if (contains(point)) return;

        visual.resetMarks();
        visual.update(root); // 현재 상태 스냅샷

        Rectangle m = rectFrom(point);
        java.util.List<Node> path = new ArrayList<>();
        Node leaf = chooseLeaf(root, m, path);

        visual.showPath(path); // 삽입 경로 시각화

        leaf.entries.add(new Entry(m, point));
        leaf.recompute();

        Node split = null;
        if (leaf.entries.size() > MAX_ENTRIES) {
            split = splitNode(leaf);
            visual.flashSplit(leaf, split);
        }

        Node cur = leaf, nn = split;
        while (true) {
            if (cur.parent == null) {
                if (nn != null) {
                    Node newRoot = new Node(false);
                    newRoot.entries.add(new Entry(copyRect(cur.mbr), cur));
                    newRoot.entries.add(new Entry(copyRect(nn.mbr), nn));
                    cur.parent = newRoot;
                    nn.parent = newRoot;
                    newRoot.recompute();
                    root = newRoot;
                } else {
                    cur.recompute();
                    root = cur;
                }
                break;
            }
            Node p = cur.parent;
            for (Entry e : p.entries) {
                if (e.child == cur) {
                    e.mbr = copyRect(cur.mbr);
                    break;
                }
            }
            p.recompute();

            visual.markVisited(p);
            visual.update(root);
            visual.redrawStep();

            if (nn != null) {
                p.entries.add(new Entry(copyRect(nn.mbr), nn));
                nn.parent = p;
                p.recompute();

                if (p.entries.size() > MAX_ENTRIES) {
                    Node ps = splitNode(p);
                    visual.flashSplit(p, ps);
                    cur = p;
                    nn = ps;
                } else {
                    cur = p;
                    nn = null;
                }
            } else {
                cur = p;
            }
        }

        size++;
        visual.setLastInserted(point);
        visual.update(root);
        visual.redrawStep();
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        visual.resetMarks();
        visual.setQuery(rectangle);
        visual.update(root);

        ArrayList<Point> results = new ArrayList<>();
        Deque<Node> dq = new ArrayDeque<>();
        dq.add(root);

        while (!dq.isEmpty()) {
            Node n = dq.pollFirst();
            if (n == null || n.mbr == null) continue;

            if (!intersects(n.mbr, rectangle)) {
                visual.markPruned(n);
                visual.update(root);
                visual.redrawStep();
                continue;
            }

            visual.markVisited(n);
            visual.update(root);
            visual.redrawStep();

            if (n.isLeaf) {
                for (Entry e : n.entries) {
                    Point p = e.userPoint;
                    if (p != null && rectContains(rectangle, p)) {
                        results.add(p);
                    }
                }
            } else {
                for (Entry e : n.entries) {
                    if (e.child != null && e.child.mbr != null &&
                            intersects(e.child.mbr, rectangle)) {
                        dq.addLast(e.child);
                    } else if (e.child != null) {
                        visual.markPruned(e.child);
                        visual.update(root);
                        visual.redrawStep();
                    }
                }
            }
        }
        return results.iterator();
    }

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        if (source == null || maxCount <= 0) {
            return Collections.<Point>emptyList().iterator();
        }
        if (root.mbr == null) {
            return Collections.<Point>emptyList().iterator();
        }

        visual.resetMarks();
        visual.setSource(source);
        visual.update(root);

        ArrayList<Point> knn = new ArrayList<>();
        double sx = source.getX();
        double sy = source.getY();

        PriorityQueue<Object[]> pq =
                new PriorityQueue<>(Comparator.comparingDouble(a -> (double) a[0]));

        pq.add(new Object[]{ mindist(root.mbr, source), 0, root });

        while (!pq.isEmpty() && knn.size() < maxCount) {
            Object[] it = pq.poll();
            int type = (int) it[1];

            if (type == 0) {
                Node n = (Node) it[2];
                if (n == null || n.mbr == null) continue;

                visual.markVisited(n);
                visual.update(root);
                visual.redrawStep(); // 노드 팝 단계

                if (n.isLeaf) {
                    for (Entry e : n.entries) {
                        Point p = e.userPoint;
                        if (p == null) continue;
                        double d = Math.hypot(sx - p.getX(), sy - p.getY());
                        pq.add(new Object[]{ d, 1, e });
                    }
                } else {
                    for (Entry e : n.entries) {
                        if (e.child != null && e.child.mbr != null) {
                            double d = mindist(e.child.mbr, source);
                            pq.add(new Object[]{ d, 0, e.child });
                        }
                    }
                }
            } else {
                Entry e = (Entry) it[2];
                if (e.userPoint != null) {
                    knn.add(e.userPoint);
                    visual.setKnn(knn);
                    visual.update(root);
                    visual.redrawStep(); // NN 확정 단계
                }
            }
        }

        return knn.iterator();
    }

    @Override
    public void delete(Point point) {
        if (point == null) return;

        visual.resetMarks();
        visual.update(root);   // 현재 상태 먼저 그림

        // 1. 리프 찾기 + 경로 하이라이트
        List<Node> path = new ArrayList<>();
        Node leaf = findLeafTrace(root, point, path);
        if (leaf == null) return;
        visual.showPath(path);

        // 2. 리프에서 엔트리 제거
        Entry target = null;
        for (Entry e : leaf.entries) {
            if (same(e.userPoint, point)) {
                target = e;
                break;
            }
        }
        if (target == null) return;
        leaf.entries.remove(target);
        size--;

        // 3. "어느 점을 지웠는지" 표시 켜기
        visual.setLastDeleted(point);
        visual.update(root);
        visual.redrawStep();   // 여기서 첫 화면: 지운 점 + 아직 구조변경 전 트리

        // 4. CondenseTree + 재삽입 (트리 구조 변경), 진행 과정은 그대로
        condenseTree(leaf);

        // 5. 루트 수축
        while (!root.isLeaf && root.entries.size() == 1) {
            root = root.entries.get(0).child;
            root.parent = null;
        }
        if (root != null) root.recompute();

        // 6. 마지막으로 deleted 마커 끄고, 최종 상태 한 번 보여주기
        visual.setLastDeleted(null);
        visual.update(root);
        visual.redrawStep();
    }



    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    // ---------- internal helpers ----------

    private Node chooseLeaf(Node n, Rectangle m, java.util.List<Node> path) {
        path.add(n);
        if (n.isLeaf) return n;

        Entry best = null;
        double bestEnl = Double.POSITIVE_INFINITY;
        double bestArea = Double.POSITIVE_INFINITY;

        for (Entry e : n.entries) {
            double a = area(e.mbr);
            double enl = enlargement(e.mbr, m);
            if (enl < bestEnl || (Math.abs(enl - bestEnl) < 1e-9 && a < bestArea)) {
                best = e;
                bestEnl = enl;
                bestArea = a;
            }
        }

        return chooseLeaf(best.child, m, path);
    }

    private Node splitNode(Node n) {
        ArrayList<Entry> E = new ArrayList<>(n.entries);
        n.entries.clear();

        int i1 = -1, i2 = -1;
        double worst = -1;

        for (int i = 0; i < E.size(); i++) {
            for (int j = i + 1; j < E.size(); j++) {
                double d = enlargement(E.get(i).mbr, E.get(j).mbr);
                if (d > worst) {
                    worst = d;
                    i1 = i;
                    i2 = j;
                }
            }
        }
        Entry a = E.remove(i2);
        Entry b = E.remove(i1);

        Node g1 = n;
        Node g2 = new Node(n.isLeaf);
        g2.parent = n.parent;

        g1.entries.add(b);
        g1.recompute();
        g2.entries.add(a);
        g2.recompute();

        while (!E.isEmpty()) {
            if (g1.entries.size() + E.size() == MIN_ENTRIES) {
                g1.entries.addAll(E);
                E.clear();
                g1.recompute();
                break;
            }
            if (g2.entries.size() + E.size() == MIN_ENTRIES) {
                g2.entries.addAll(E);
                E.clear();
                g2.recompute();
                break;
            }

            Entry pick = null;
            double diff = -1;
            for (Entry cand : E) {
                double d1 = enlargement(g1.mbr, cand.mbr);
                double d2 = enlargement(g2.mbr, cand.mbr);
                double dv = Math.abs(d1 - d2);
                if (dv > diff) {
                    diff = dv;
                    pick = cand;
                }
            }
            E.remove(pick);
            double inc1 = enlargement(g1.mbr, pick.mbr);
            double inc2 = enlargement(g2.mbr, pick.mbr);
            if (inc1 < inc2 || (Math.abs(inc1 - inc2) < 1e-9 && area(g1.mbr) < area(g2.mbr))) {
                g1.entries.add(pick);
                g1.recompute();
            } else {
                g2.entries.add(pick);
                g2.recompute();
            }
        }

        if (!n.isLeaf) {
            for (Entry e : g1.entries) if (e.child != null) e.child.parent = g1;
            for (Entry e : g2.entries) if (e.child != null) e.child.parent = g2;
        }
        return g2;
    }

    private Node findLeaf(Node n, Point p) {
        if (n.isLeaf) {
            for (Entry e : n.entries) if (same(e.userPoint, p)) return n;
            return null;
        }
        for (Entry e : n.entries) {
            if (e.child != null && e.child.mbr != null &&
                    rectContains(e.child.mbr, p)) {
                Node f = findLeaf(e.child, p);
                if (f != null) return f;
            }
        }
        return null;
    }

    private Node findLeafTrace(Node n, Point p, java.util.List<Node> path) {
        path.add(n);
        if (n.isLeaf) {
            for (Entry e : n.entries) if (same(e.userPoint, p)) return n;
            return null;
        }
        for (Entry e : n.entries) {
            if (e.child != null && e.child.mbr != null &&
                    rectContains(e.child.mbr, p)) {
                Node f = findLeafTrace(e.child, p, path);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void condenseTree(Node n) {
        ArrayList<Node> reinsertSubtrees = new ArrayList<>();
        Node cur = n;
        while (cur != null) {
            if (cur != root && cur.entries.size() < MIN_ENTRIES) {
                visual.flashUnderflow(cur);

                Node parent = cur.parent;
                Entry link = null;
                for (Entry e : parent.entries) {
                    if (e.child == cur) {
                        link = e;
                        break;
                    }
                }
                if (link != null) parent.entries.remove(link);

                if (cur.isLeaf) {
                    for (Entry e : cur.entries) {
                        if (e.userPoint != null) {
                            visual.flashReinsert(e.userPoint);
                            reinsertPoint(e.userPoint);
                        }
                    }
                } else {
                    for (Entry e : cur.entries) {
                        reinsertSubtrees.add(e.child);
                    }
                }
                cur.entries.clear();
                parent.recompute();
                visual.update(root);
                visual.redrawStep();
                cur = parent;
            } else {
                cur.recompute();
                visual.update(root);
                visual.redrawStep();
                cur = cur.parent;
            }
        }

        for (Node s : reinsertSubtrees) collectReinsert(s);
    }

    private void collectReinsert(Node n) {
        if (n == null) return;
        if (n.isLeaf) {
            for (Entry e : n.entries) {
                if (e.userPoint != null) {
                    visual.flashReinsert(e.userPoint);
                    reinsertPoint(e.userPoint);
                }
            }
        } else {
            for (Entry e : n.entries) {
                collectReinsert(e.child);
            }
        }
    }

    private void reinsertPoint(Point p) {
        Rectangle m = rectFrom(p);
        java.util.List<Node> path = new ArrayList<>();
        Node where = chooseLeaf(root, m, path);
        visual.showPath(path);

        where.entries.add(new Entry(m, p));
        where.recompute();
        Node sp = null;
        if (where.entries.size() > MAX_ENTRIES) {
            sp = splitNode(where);
            visual.flashSplit(where, sp);
        }
        adjustTree(where, sp);
        visual.update(root);
        visual.redrawStep();
    }

    private void adjustTree(Node n, Node nn) {
        Node cur = n, split = nn;
        while (true) {
            if (cur.parent == null) {
                if (split != null) {
                    Node newRoot = new Node(false);
                    newRoot.entries.add(new Entry(copyRect(cur.mbr), cur));
                    newRoot.entries.add(new Entry(copyRect(split.mbr), split));
                    cur.parent = newRoot;
                    split.parent = newRoot;
                    newRoot.recompute();
                    root = newRoot;
                } else {
                    cur.recompute();
                    root = cur;
                }
                return;
            }
            Node p = cur.parent;
            for (Entry e : p.entries) {
                if (e.child == cur) {
                    e.mbr = copyRect(cur.mbr);
                    break;
                }
            }
            p.recompute();

            if (split != null) {
                p.entries.add(new Entry(copyRect(split.mbr), split));
                split.parent = p;
                if (p.entries.size() > MAX_ENTRIES) {
                    Node ps = splitNode(p);
                    cur = p;
                    split = ps;
                } else {
                    cur = p;
                    split = null;
                }
            } else {
                cur = p;
            }
        }
    }

    private boolean contains(Point p) {
        return findLeaf(root, p) != null;
    }
}
