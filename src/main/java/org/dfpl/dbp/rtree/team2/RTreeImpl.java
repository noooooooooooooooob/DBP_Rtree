package org.dfpl.dbp.rtree.team2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class RTreeImpl implements RTree {

    // 상수 정의 (4-way R-Tree)
    private static final int M = 4;
    private static final int m = 2;

    private Node root;
    private int size;

    // 시각화 객체
    private RTreeVisualizer visualizer;

    // 시각화 지연 시간 설정
    private static final int DELAY_ADD_DELETE = 100;
    // private static final int DELAY_KNN_STEP = 300; // KNN 단계 시각화 제거로 사용 안함
    private static final int DELAY_TASK_END = 1500;

    public RTreeImpl() {
        this.size = 0;
        SwingUtilities.invokeLater(() -> {
            this.visualizer = new RTreeVisualizer();
            this.visualizer.setVisible(true);
        });
        sleep(500);
    }

    // ---------------------------------------------------------
    // Snapshot용 데이터 구조 (시각화 스레드 안전성 확보)
    // ---------------------------------------------------------
    private static class SNode {
        Rectangle mbr;
        boolean isLeaf;
        List<Point> points = new ArrayList<>();
        List<SNode> children = new ArrayList<>();
    }

    // ---------------------------------------------------------
    // 내부 데이터 구조 (Entry, Node)
    // ---------------------------------------------------------
    private class Entry {
        Rectangle mbr;
        Point point;
        Node child;

        public Entry(Point point) {
            this.point = point;
            this.mbr = new Rectangle(point, point);
            this.child = null;
        }
        public Entry(Node child) {
            this.point = null;
            this.mbr = child.mbr;
            this.child = child;
        }
    }

    private class Node {
        Node parent;
        boolean isLeaf;
        List<Entry> entries;
        Rectangle mbr;

        public Node(boolean isLeaf, Node parent) {
            this.isLeaf = isLeaf;
            this.parent = parent;
            this.entries = new ArrayList<>(M + 1);
            this.mbr = null;
        }

        public void updateMbr() {
            if (entries.isEmpty()) {
                this.mbr = null;
                return;
            }
            Rectangle newMbr = null;
            for (Entry e : entries) {
                newMbr = (newMbr == null) ? e.mbr : unionRect(newMbr, e.mbr);
            }
            this.mbr = newMbr;
        }
    }

    // KNN 우선순위 큐 항목
    private class PQEntry {
        Node node;
        Point point;
        double distanceSq;

        public PQEntry(Node node, double d) { this.node = node; this.distanceSq = d; }
        public PQEntry(Point point, double d) { this.point = point; this.distanceSq = d; }
        public boolean isPoint() { return point != null; }
    }

    // ---------------------------------------------------------
    // 기하 연산 헬퍼
    // ---------------------------------------------------------
    private double area(Rectangle r) {
        if(r==null) return 0;
        return (r.getRightBottom().getX() - r.getLeftTop().getX()) * (r.getRightBottom().getY() - r.getLeftTop().getY());
    }

    private Rectangle unionRect(Rectangle r1, Rectangle r2) {
        if (r1 == null) return r2; if (r2 == null) return r1;
        return new Rectangle(
                new Point(Math.min(r1.getLeftTop().getX(), r2.getLeftTop().getX()), Math.min(r1.getLeftTop().getY(), r2.getLeftTop().getY())),
                new Point(Math.max(r1.getRightBottom().getX(), r2.getRightBottom().getX()), Math.max(r1.getRightBottom().getY(), r2.getRightBottom().getY()))
        );
    }

    private double getEnlargement(Rectangle r, Point p) {
        Rectangle u = unionRect(r, new Rectangle(p, p));
        return area(u) - area(r);
    }

    private double getEnlargement(Rectangle r1, Rectangle r2) {
        return area(unionRect(r1, r2)) - area(r1);
    }

    private boolean intersects(Rectangle r1, Rectangle r2) {
        if(r1==null || r2==null) return false;
        return r1.getLeftTop().getX() <= r2.getRightBottom().getX() && r1.getRightBottom().getX() >= r2.getLeftTop().getX() &&
                r1.getLeftTop().getY() <= r2.getRightBottom().getY() && r1.getRightBottom().getY() >= r2.getLeftTop().getY();
    }

    private boolean contains(Rectangle r, Point p) {
        return p.getX() >= r.getLeftTop().getX() && p.getX() <= r.getRightBottom().getX() &&
                p.getY() >= r.getLeftTop().getY() && p.getY() <= r.getRightBottom().getY();
    }

    private double distSq(Point p1, Point p2) {
        double dx = p1.getX()-p2.getX(); double dy = p1.getY()-p2.getY();
        return dx*dx + dy*dy;
    }

    private double minDistSq(Rectangle r, Point p) {
        double dx = Math.max(Math.max(r.getLeftTop().getX() - p.getX(), 0), p.getX() - r.getRightBottom().getX());
        double dy = Math.max(Math.max(r.getLeftTop().getY() - p.getY(), 0), p.getY() - r.getRightBottom().getY());
        return dx*dx + dy*dy;
    }

    // ---------------------------------------------------------
    // Task 1: Add (Quadratic Split 적용)
    // ---------------------------------------------------------
    @Override
    public void add(Point point) {
        if (visualizer != null) visualizer.setOperation("ADD", point);

        if (root == null) {
            root = new Node(true, null);
            root.entries.add(new Entry(point));
            root.updateMbr();
            size++;
        } else {
            Node leaf = chooseLeaf(root, point);
            leaf.entries.add(new Entry(point));
            size++;
            if (leaf.entries.size() > M) {
                Node newNode = splitNode(leaf);
                adjustTree(leaf, newNode);
            } else {
                adjustTree(leaf, null);
            }
        }
        updateVisualizer(DELAY_ADD_DELETE);
    }

    private Node chooseLeaf(Node n, Point p) {
        if (n.isLeaf) return n;
        double minEnlargement = Double.MAX_VALUE;
        double minArea = Double.MAX_VALUE;
        Entry best = null;
        for (Entry e : n.entries) {
            double enl = getEnlargement(e.mbr, p);
            double a = area(unionRect(e.mbr, new Rectangle(p,p)));
            if (enl < minEnlargement) { minEnlargement = enl; minArea = a; best = e; }
            else if (enl == minEnlargement && a < minArea) { minArea = a; best = e; }
        }
        return chooseLeaf(best.child, p);
    }

    // [Quadratic Split] 구현
    private Node splitNode(Node n) {
        Node newNode = new Node(n.isLeaf, n.parent);
        List<Entry> allEntries = new ArrayList<>(n.entries);
        n.entries.clear();

        // 1. PickSeeds (Quadratic: 모든 쌍 비교)
        Entry seed1 = null, seed2 = null;
        double maxWaste = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < allEntries.size(); i++) {
            for (int j = i + 1; j < allEntries.size(); j++) {
                Entry e1 = allEntries.get(i);
                Entry e2 = allEntries.get(j);
                Rectangle combined = unionRect(e1.mbr, e2.mbr);
                double waste = area(combined) - area(e1.mbr) - area(e2.mbr);

                if (waste > maxWaste) {
                    maxWaste = waste;
                    seed1 = e1;
                    seed2 = e2;
                }
            }
        }

        n.entries.add(seed1);
        newNode.entries.add(seed2);
        allEntries.remove(seed1);
        allEntries.remove(seed2);
        n.updateMbr();
        newNode.updateMbr();

        // 2. PickNext & Distribute
        while (!allEntries.isEmpty()) {
            // 강제 할당 조건 확인
            if (n.entries.size() + allEntries.size() == m) {
                n.entries.addAll(allEntries);
                allEntries.clear();
                break;
            }
            if (newNode.entries.size() + allEntries.size() == m) {
                newNode.entries.addAll(allEntries);
                allEntries.clear();
                break;
            }

            // PickNext: 두 그룹에 넣었을 때 면적 증가량 차이가 가장 큰 항목 선택
            double maxDiff = Double.NEGATIVE_INFINITY;
            int bestEntryIdx = -1;

            for (int i = 0; i < allEntries.size(); i++) {
                Entry e = allEntries.get(i);
                double d1 = getEnlargement(n.mbr, e.mbr);
                double d2 = getEnlargement(newNode.mbr, e.mbr);
                double diff = Math.abs(d1 - d2);

                if (diff > maxDiff) {
                    maxDiff = diff;
                    bestEntryIdx = i;
                }
            }

            Entry bestEntry = allEntries.remove(bestEntryIdx);
            double d1 = getEnlargement(n.mbr, bestEntry.mbr);
            double d2 = getEnlargement(newNode.mbr, bestEntry.mbr);

            if (d1 < d2) {
                n.entries.add(bestEntry);
            } else if (d2 < d1) {
                newNode.entries.add(bestEntry);
            } else {
                // 면적 증가 같으면 면적 작은 쪽 -> 개수 적은 쪽
                double a1 = area(n.mbr);
                double a2 = area(newNode.mbr);
                if (a1 < a2) n.entries.add(bestEntry);
                else if (a2 < a1) newNode.entries.add(bestEntry);
                else if (n.entries.size() < newNode.entries.size()) n.entries.add(bestEntry);
                else newNode.entries.add(bestEntry);
            }
            n.updateMbr();
            newNode.updateMbr();
        }

        // 부모 포인터 갱신
        for(Entry e : n.entries) if(e.child!=null) e.child.parent = n;
        for(Entry e : newNode.entries) if(e.child!=null) e.child.parent = newNode;

        return newNode;
    }

    private void adjustTree(Node n, Node nn) {
        if (n == null) return;
        if (nn != null) {
            if (n.parent == null) {
                Node newRoot = new Node(false, null);
                newRoot.entries.add(new Entry(n)); n.parent = newRoot;
                newRoot.entries.add(new Entry(nn)); nn.parent = newRoot;
                newRoot.updateMbr();
                this.root = newRoot;
                return;
            }
            n.parent.entries.add(new Entry(nn));
        }
        n.updateMbr();
        if (n.parent != null) {
            for(Entry e : n.parent.entries) if(e.child == n) { e.mbr = n.mbr; break; }
            if (n.parent.entries.size() > M) {
                Node newParent = splitNode(n.parent);
                adjustTree(n.parent, newParent);
            } else {
                adjustTree(n.parent, null);
            }
        }
    }

    // ---------------------------------------------------------
    // Task 2: Search
    // ---------------------------------------------------------
    @Override
    public Iterator<Point> search(Rectangle rect) {
        List<Point> res = new ArrayList<>();
        if (visualizer != null) {
            visualizer.clear();
            visualizer.setSearchRect(rect);
            visualizer.setOperation("SEARCH", null);
            updateVisualizer(0);
        }

        searchRec(root, rect, res);

        if (visualizer != null) {
            visualizer.setSearchResults(res);
            updateVisualizer(DELAY_TASK_END);
        }
        return res.iterator();
    }

    private void searchRec(Node n, Rectangle r, List<Point> res) {
        if (n == null) return;

        if (n.mbr == null || !intersects(n.mbr, r)) {
            if(visualizer != null && n.mbr != null) visualizer.addPruned(n.mbr);
            return;
        }

        if(visualizer != null) visualizer.addVisited(n.mbr);

        if (n.isLeaf) {
            for (Entry e : n.entries) if (contains(r, e.point)) res.add(e.point);
        } else {
            for (Entry e : n.entries) searchRec(e.child, r, res);
        }
    }

    // ---------------------------------------------------------
    // Task 3: KNN
    // ---------------------------------------------------------
    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        List<Point> res = new LinkedList<>();
        if (root == null) return res.iterator();

        if (visualizer != null) {
            visualizer.clear();
            visualizer.setOperation("KNN (k=" + maxCount + ")", source);
        }

        PriorityQueue<PQEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e.distanceSq));
        if (root.mbr != null) {
            pq.add(new PQEntry(root, minDistSq(root.mbr, source)));
        }

        while (!pq.isEmpty()) {
            PQEntry cur = pq.poll();

            // [수정]: 초록색 점선 (KNN Step) 시각화 제거
            // if (visualizer != null) {
            // 	visualizer.addKnnStep(cur.node != null ? cur.node.mbr : new Rectangle(cur.point, cur.point));
            // 	updateVisualizer(DELAY_KNN_STEP);
            // }

            if (res.size() == maxCount && cur.distanceSq > distSq(source, res.get(maxCount-1))) break;

            if (cur.isPoint()) {
                res.add(cur.point);
                res.sort(Comparator.comparingDouble(p -> distSq(source, p)));
                if (res.size() > maxCount) ((LinkedList<Point>)res).removeLast();
            } else {
                if (cur.node.isLeaf) {
                    for (Entry e : cur.node.entries) pq.add(new PQEntry(e.point, distSq(source, e.point)));
                } else {
                    for (Entry e : cur.node.entries) pq.add(new PQEntry(e.child, minDistSq(e.mbr, source)));
                }
            }
        }

        if (visualizer != null) {
            visualizer.setKnnResults(res);
            updateVisualizer(DELAY_TASK_END);
        }
        return res.iterator();
    }

    // ---------------------------------------------------------
    // Task 4: Delete
    // ---------------------------------------------------------
    @Override
    public void delete(Point point) {
        if(visualizer != null) visualizer.setOperation("DELETE", point);
        if (root == null) return;

        Node leaf = findLeaf(root, point);
        if (leaf == null) return;

        Entry target = null;
        for(Entry e : leaf.entries) if(e.point.getX() == point.getX() && e.point.getY() == point.getY()) target = e;
        if(target == null) return;

        leaf.entries.remove(target);
        size--;

        List<Entry> reinsert = new ArrayList<>();
        condenseTree(leaf, reinsert);

        for(Entry e : reinsert) {
            if(e.point != null) add(e.point);
        }

        updateVisualizer(DELAY_ADD_DELETE);
    }

    private Node findLeaf(Node n, Point p) {
        if(n.isLeaf) {
            for(Entry e : n.entries) if(e.point.getX()==p.getX() && e.point.getY()==p.getY()) return n;
            return null;
        }
        for(Entry e : n.entries) {
            if(contains(e.mbr, p)) {
                Node res = findLeaf(e.child, p);
                if(res != null) return res;
            }
        }
        return null;
    }

    private void condenseTree(Node n, List<Entry> reinsert) {
        if(n == root) {
            if(!n.isLeaf && n.entries.size()==1) {
                root = n.entries.get(0).child;
                root.parent = null;
            } else if (n.entries.isEmpty()) {
                root = null;
            }
            return;
        }
        if(n.entries.size() < m) {
            reinsert.addAll(n.entries);
            Node p = n.parent;
            p.entries.removeIf(e -> e.child == n);
            condenseTree(p, reinsert);
        } else {
            adjustTree(n, null);
        }
    }

    @Override
    public boolean isEmpty() { return size == 0; }

    // ---------------------------------------------------------
    // GUI Visualization Logic (Snapshot + Auto Fit 적용)
    // ---------------------------------------------------------

    private void updateVisualizer(long delay) {
        if(visualizer != null) {
            SNode rootSnapshot = (root != null) ? captureSnapshot(root) : null;
            visualizer.updateTreeSnapshot(rootSnapshot);

            sleep(delay);
        }
    }

    private SNode captureSnapshot(Node node) {
        if (node == null) return null;
        SNode sNode = new SNode();
        sNode.mbr = node.mbr;
        sNode.isLeaf = node.isLeaf;

        if (node.isLeaf) {
            for(Entry e : node.entries) {
                sNode.points.add(e.point);
            }
        } else {
            for(Entry e : node.entries) {
                sNode.children.add(captureSnapshot(e.child));
            }
        }
        return sNode;
    }

    private void sleep(long t) {
        try { Thread.sleep(t); } catch(Exception e) {}
    }

    class RTreeVisualizer extends JFrame {
        VisualPanel panel;
        public RTreeVisualizer() {
            panel = new VisualPanel();
            add(panel);
            setSize(800, 800);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setTitle("4-Way R-Tree (Quadratic / Snapshot / AutoFit)");
        }

        public void updateTreeSnapshot(SNode root) { panel.rootSnapshot = root; panel.repaint(); }
        public void setOperation(String op, Point p) { panel.op = op; panel.lastP = p; }
        public void setSearchRect(Rectangle r) { panel.searchRect = r; }
        public void addPruned(Rectangle r) { panel.pruned.add(r); }
        public void addVisited(Rectangle r) { panel.visited.add(r); }
        public void setSearchResults(List<Point> l) { panel.searchResults = l; }
        public void addKnnStep(Rectangle r) { panel.knnSteps.add(r); }
        public void setKnnResults(List<Point> l) { panel.knnResults = l; }
        public void clear() { panel.clear(); }
    }

    class VisualPanel extends JPanel {
        SNode rootSnapshot;

        String op = "Ready"; Point lastP;
        Rectangle searchRect;
        List<Rectangle> pruned = new ArrayList<>();
        List<Rectangle> visited = new ArrayList<>();
        List<Point> searchResults = new ArrayList<>();
        List<Rectangle> knnSteps = new ArrayList<>();
        List<Point> knnResults = new ArrayList<>();

        Color[] cols = { new Color(255,0,0,40), new Color(0,255,0,40), new Color(0,0,255,40), new Color(255,255,0,40) };

        public VisualPanel() { setBackground(Color.WHITE); }
        public void clear() {
            searchRect=null; pruned.clear(); visited.clear(); searchResults.clear();
            knnSteps.clear(); knnResults.clear();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // [Auto Fit Logic]
            double minX = 0, minY = 0, maxX = 100, maxY = 100;
            if (rootSnapshot != null && rootSnapshot.mbr != null) {
                minX = rootSnapshot.mbr.getLeftTop().getX();
                minY = rootSnapshot.mbr.getLeftTop().getY();
                maxX = rootSnapshot.mbr.getRightBottom().getX();
                maxY = rootSnapshot.mbr.getRightBottom().getY();
            }
            maxX = Math.max(maxX, 200); maxY = Math.max(maxY, 200);

            double dataW = maxX - minX;
            double dataH = maxY - minY;

            double margin = 50;
            double viewW = getWidth() - 2 * margin;
            double viewH = getHeight() - 2 * margin;

            double scaleX = viewW / dataW;
            double scaleY = viewH / dataH;
            double scale = Math.min(scaleX, scaleY);

            // 좌표 변환 적용
            g2.translate(margin, getHeight() - margin);
            g2.scale(scale, -scale);
            g2.translate(-minX, -minY);

            // ---------------------------------------------------
            // 그리기 시작

            // Task 2: 가지치기 & 방문
            g2.setColor(new Color(200,200,200,100));
            for(Rectangle r: pruned) fillRect(g2, r);
            g2.setColor(new Color(0,0,255,50));
            for(Rectangle r: visited) fillRect(g2, r);

            // Tree Drawing (Snapshot 사용)
            if(rootSnapshot != null) drawNode(g2, rootSnapshot, 0);

            // Task 2: Search Box
            if(searchRect != null) {
                g2.setColor(Color.BLUE);
                g2.setStroke(new BasicStroke((float)(2.0/scale)));
                drawRect(g2, searchRect);
            }
            g2.setColor(Color.RED);
            for(Point p: searchResults) fillCircle(g2, p, 4.0/scale);

            // Task 3: KNN Process (초록색 점선 - 제거됨)
            // 이전 코드:
            // g2.setColor(Color.GREEN.darker());
            // g2.setStroke(new BasicStroke((float)(1.0/scale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{(float)(5.0/scale)}, 0));
            // for(Rectangle r: knnSteps) drawRect(g2, r);

            // [수정]: KNN 결과만 표시
            g2.setStroke(new BasicStroke((float)(1.0/scale)));
            g2.setColor(Color.MAGENTA);
            for(Point p: knnResults) { fillCircle(g2, p, 5.0/scale); }

            // Status Text (스케일 복구 후 그림)
            g2.setTransform(new AffineTransform());

            g2.setColor(Color.BLACK);
            // [수정]: (20, 30) -> (50, 20)로 오른쪽 위 이동
            g2.drawString("Op: " + op + (lastP!=null?" "+lastP:""), 50, 20);
        }

        void drawNode(Graphics2D g, SNode n, int lvl) {
            if(n.mbr == null) return;
            g.setColor(cols[Math.min(lvl,3)]); fillRect(g, n.mbr);

            g.setColor(cols[Math.min(lvl,3)].darker().darker());

            double scaleY = Math.abs(g.getTransform().getScaleY());
            g.setStroke(new BasicStroke((float)( (lvl==0?2:1) / scaleY )));

            drawRect(g, n.mbr);
            if(n.isLeaf) {
                g.setColor(Color.BLACK);
                for(Point p : n.points) fillCircle(g, p, 3.0/scaleY);
            } else {
                for(SNode child : n.children) drawNode(g, child, lvl+1);
            }
        }

        void fillRect(Graphics2D g, Rectangle r) { g.fillRect((int)r.getLeftTop().getX(), (int)r.getLeftTop().getY(), (int)(r.getRightBottom().getX()-r.getLeftTop().getX()), (int)(r.getRightBottom().getY()-r.getLeftTop().getY())); }
        void drawRect(Graphics2D g, Rectangle r) { g.drawRect((int)r.getLeftTop().getX(), (int)r.getLeftTop().getY(), (int)(r.getRightBottom().getX()-r.getLeftTop().getX()), (int)(r.getRightBottom().getY()-r.getLeftTop().getY())); }
        void fillCircle(Graphics2D g, Point p, double r) {
            g.fillOval((int)(p.getX()-r), (int)(p.getY()-r), (int)(r*2), (int)(r*2));
        }
    }
}