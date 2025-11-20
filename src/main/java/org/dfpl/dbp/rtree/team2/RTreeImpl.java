package org.dfpl.dbp.rtree.team2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    private Node root;
    private static final int M = 4; // 4-way R-Tree (최대 항목 수)
    private static final int m = 2; // 최소 항목 수 (M/2)
    private int size;

    /**
     * GUI 시각화 객체
     */
    private RTreeVisualizer visualizer;

    /**
     * 시각화를 위한 지연 시간 (ms). add/delete 시 각 스텝.
     */
    private static final int VISUAL_DELAY = 100; // 100ms

    /**
     * Task 2, 3 완료 후 결과 확인을 위한 지연 시간
     */
    private static final int TASK_COMPLETION_DELAY = 1500; // 1.5초


    // --- R-Tree 생성자 ---
    public RTreeImpl() {
        this.size = 0;

        // GUI 초기화 (Swing EDT에서 실행)
        SwingUtilities.invokeLater(() -> {
            this.visualizer = new RTreeVisualizer(this);
            this.visualizer.setVisible(true);
        });

        // GUI가 생성될 시간을 약간 기다립니다.
        sleep(500);
    }

    // --- R-Tree 내부 구조 ---

    // 노드의 항목 (Entry)
    private class Entry {
        Rectangle mbr;
        Point point; // 리프 노드 항목용 (데이터)
        Node child;  // 내부 노드 항목용 (자식 포인터)

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

    // R-Tree 노드 (Node)
    private class Node {
        Node parent;
        boolean isLeaf;
        List<Entry> entries;
        Rectangle mbr;

        public Node(boolean isLeaf, Node parent) {
            this.isLeaf = isLeaf;
            this.parent = parent;
            this.entries = new ArrayList<>(M + 1); // 분할 대비 M+1
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

    // KNN 검색용 우선순위 큐 항목
    private class PQEntry {
        Node node;
        Point point;
        double distanceSq; // 거리 제곱

        public PQEntry(Node node, double distanceSq) {
            this.node = node; this.point = null; this.distanceSq = distanceSq;
        }
        public PQEntry(Point point, double distanceSq) {
            this.node = null; this.point = point; this.distanceSq = distanceSq;
        }
        public boolean isPoint() { return point != null; }
    }

    // --- 기하학적 유틸리티 ---

    private double calculateArea(Rectangle rect) {
        if (rect == null) return 0.0;
        return (rect.getRightBottom().getX() - rect.getLeftTop().getX()) * (rect.getRightBottom().getY() - rect.getLeftTop().getY());
    }

    private Rectangle unionRect(Rectangle r1, Rectangle r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        double minX = Math.min(r1.getLeftTop().getX(), r2.getLeftTop().getX());
        double minY = Math.min(r1.getLeftTop().getY(), r2.getLeftTop().getY());
        double maxX = Math.max(r1.getRightBottom().getX(), r2.getRightBottom().getX());
        double maxY = Math.max(r1.getRightBottom().getY(), r2.getRightBottom().getY());
        return new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
    }

    private Rectangle unionRectWithPoint(Rectangle r, Point p) {
        if (r == null) return new Rectangle(p, p);
        double minX = Math.min(r.getLeftTop().getX(), p.getX());
        double minY = Math.min(r.getLeftTop().getY(), p.getY());
        double maxX = Math.max(r.getRightBottom().getX(), p.getX());
        double maxY = Math.max(r.getRightBottom().getY(), p.getY());
        return new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
    }

    private double getEnlargement(Rectangle r, Point p) {
        return calculateArea(unionRectWithPoint(r, p)) - calculateArea(r);
    }

    private double getEnlargement(Rectangle r1, Rectangle r2) {
        return calculateArea(unionRect(r1, r2)) - calculateArea(r1);
    }

    private boolean intersects(Rectangle r1, Rectangle r2) {
        if (r1 == null || r2 == null) return false;
        return r1.getLeftTop().getX() <= r2.getRightBottom().getX() &&
                r1.getRightBottom().getX() >= r2.getLeftTop().getX() &&
                r1.getLeftTop().getY() <= r2.getRightBottom().getY() &&
                r1.getRightBottom().getY() >= r2.getLeftTop().getY();
    }

    private boolean containsPoint(Rectangle r, Point p) {
        return p.getX() >= r.getLeftTop().getX() &&
                p.getX() <= r.getRightBottom().getX() &&
                p.getY() >= r.getLeftTop().getY() &&
                p.getY() <= r.getRightBottom().getY();
    }

    private double distanceSq(Point p1, Point p2) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        return dx * dx + dy * dy;
    }

    private double minDistSq(Rectangle rect, Point p) {
        double dx = 0;
        if (p.getX() < rect.getLeftTop().getX()) dx = rect.getLeftTop().getX() - p.getX();
        else if (p.getX() > rect.getRightBottom().getX()) dx = p.getX() - rect.getRightBottom().getX();
        double dy = 0;
        if (p.getY() < rect.getLeftTop().getY()) dy = rect.getLeftTop().getY() - p.getY();
        else if (p.getY() > rect.getRightBottom().getY()) dy = p.getY() - rect.getRightBottom().getY();
        return dx * dx + dy * dy;
    }

    private boolean pointsEqual(Point p1, Point p2) {
        return p1.getX() == p2.getX() && p1.getY() == p2.getY();
    }

    // --- Task 1: Add (삽입) ---

    @Override
    public void add(Point point) {
        // GUI: 작업 시작 알림
        if (visualizer != null) visualizer.setLastOperation("add", point);

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
                Node newNode = splitNode(leaf); // Linear Split
                adjustTree(leaf, newNode);
            } else {
                adjustTree(leaf, null); // MBR만 갱신
            }
        }

        // GUI: 갱신 및 시각적 지연
        updateVisualizer(VISUAL_DELAY);
    }

    private Node chooseLeaf(Node n, Point p) {
        if (n.isLeaf) return n;

        double minEnlargement = Double.MAX_VALUE;
        double minArea = Double.MAX_VALUE;
        Entry bestEntry = null;

        for (Entry e : n.entries) {
            double enlargement = getEnlargement(e.mbr, p);
            double area = calculateArea(unionRectWithPoint(e.mbr, p));

            if (enlargement < minEnlargement) {
                minEnlargement = enlargement; minArea = area; bestEntry = e;
            } else if (enlargement == minEnlargement && area < minArea) {
                minArea = area; bestEntry = e;
            }
        }

        return chooseLeaf(bestEntry.child, p);
    }

    private Node splitNode(Node n) {
        Node newNode = new Node(n.isLeaf, n.parent);
        List<Entry> allEntries = new ArrayList<>(n.entries);
        n.entries.clear();

        // 1. PickSeeds: Linear Split (단순히 X, Y축 기준 최대 분리)
        Entry seed1 = null, seed2 = null;
        double maxSep = -1;

        // X축 기준
        allEntries.sort(Comparator.comparingDouble(e -> e.mbr.getLeftTop().getX()));
        double sepX = allEntries.get(allEntries.size()-1).mbr.getLeftTop().getX() - allEntries.get(0).mbr.getRightBottom().getX();
        maxSep = sepX;
        seed1 = allEntries.get(0);
        seed2 = allEntries.get(allEntries.size()-1);

        // Y축 기준
        allEntries.sort(Comparator.comparingDouble(e -> e.mbr.getLeftTop().getY()));
        double sepY = allEntries.get(allEntries.size()-1).mbr.getLeftTop().getY() - allEntries.get(0).mbr.getRightBottom().getY();

        if (sepY > maxSep) { // Y축 분리가 더 좋으면
            seed1 = allEntries.get(0);
            seed2 = allEntries.get(allEntries.size()-1);
        }

        n.entries.add(seed1);
        newNode.entries.add(seed2);
        allEntries.remove(seed1);
        allEntries.remove(seed2);

        n.updateMbr();
        newNode.updateMbr();

        // 2. AssignRemaining
        while (!allEntries.isEmpty()) {
            Entry nextEntry = allEntries.remove(0);

            double enlargement1 = getEnlargement(n.mbr, nextEntry.mbr);
            double enlargement2 = getEnlargement(newNode.mbr, nextEntry.mbr);

            boolean forceToN = (n.entries.size() + allEntries.size() == m);
            boolean forceToNew = (newNode.entries.size() + allEntries.size() == m);

            if (forceToN || (!forceToNew && enlargement1 < enlargement2)) {
                n.entries.add(nextEntry);
            } else if (forceToNew || (!forceToN && enlargement2 < enlargement1)) {
                newNode.entries.add(nextEntry);
            } else { // 비용이 같으면 면적이 작은 쪽에
                if (calculateArea(n.mbr) <= calculateArea(newNode.mbr)) {
                    n.entries.add(nextEntry);
                } else {
                    newNode.entries.add(nextEntry);
                }
            }
        }

        n.updateMbr();
        newNode.updateMbr();

        // 자식 노드의 부모 포인터 갱신 (내부 노드 분할의 경우)
        for(Entry e : n.entries) { if (e.child != null) e.child.parent = n; }
        for(Entry e : newNode.entries) { if (e.child != null) e.child.parent = newNode; }

        return newNode;
    }

    private void adjustTree(Node n, Node nn) {
        if (n == null) return;

        Node parent = n.parent;

        if (nn != null) { // 분할이 발생한 경우
            if (parent == null) { // 루트가 분할됨
                Node newRoot = new Node(false, null);
                newRoot.entries.add(new Entry(n)); n.parent = newRoot;
                newRoot.entries.add(new Entry(nn)); nn.parent = newRoot;
                newRoot.updateMbr();
                this.root = newRoot;
                return;
            }
            parent.entries.add(new Entry(nn));
        }

        n.updateMbr();

        if (parent != null) {
            // 부모 Entry의 MBR 갱신
            for(Entry e : parent.entries) {
                if (e.child == n) {
                    e.mbr = n.mbr;
                    break;
                }
            }

            if (parent.entries.size() > M) {
                Node newParentNode = splitNode(parent);
                adjustTree(parent, newParentNode);
            } else {
                adjustTree(parent, null);
            }
        }
    }


    // --- Task 2: Search (범위 탐색) ---

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        List<Point> result = new ArrayList<>();

        // GUI: 탐색 상태 초기화
        if (visualizer != null) {
            visualizer.clearVisuals();
            visualizer.setLastOperation("search", null); // 상태 표시
            visualizer.setSearchRect(rectangle);
        }

        if (root != null) {
            searchRecursive(root, rectangle, result);
        }

        // GUI: 탐색 완료 후 갱신 및 확인
        if (visualizer != null) {
            visualizer.setSearchResults(result);
            visualizer.repaint();
            sleep(TASK_COMPLETION_DELAY);
        }

        return result.iterator();
    }

    private void searchRecursive(Node n, Rectangle searchRect, List<Point> result) {

        if (n.mbr == null || !intersects(n.mbr, searchRect)) {
            // GUI: (요건 2.5) 가지치기 되는 영역 부각
            if (visualizer != null) visualizer.addPrunedNode(n.mbr);
            return;
        }

        // GUI: 방문한 노드 MBR 기록
        if (visualizer != null) visualizer.addVisitedNode(n.mbr);

        if (n.isLeaf) {
            for (Entry e : n.entries) {
                if (containsPoint(searchRect, e.point)) {
                    result.add(e.point);
                }
            }
        } else {
            for (Entry e : n.entries) {
                searchRecursive(e.child, searchRect, result);
            }
        }
    }


    // --- Task 3: Nearest (KNN 검색) ---

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        List<Point> result = new LinkedList<>();
        if (root == null || maxCount <= 0) return result.iterator();

        // GUI: KNN 상태 초기화
        if (visualizer != null) {
            visualizer.clearVisuals();
            visualizer.setLastOperation("knn", source); // 상태 표시
            visualizer.setKNNSettings(source, maxCount);
        }

        PriorityQueue<PQEntry> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e.distanceSq));
        pq.add(new PQEntry(root, minDistSq(root.mbr, source)));

        while (!pq.isEmpty()) {
            PQEntry current = pq.poll();

            // GUI: (요건 3.5) 탐색 과정(PQ에서 꺼낸 항목) 기록
            if (visualizer != null) visualizer.addKnnSearchProcess(current);

            // 가지치기: maxCount개를 찾았고, PQ의 top 거리가 마지막 결과보다 멀면 종료
            if (result.size() == maxCount) {
                double maxDistSqInResult = distanceSq(source, result.get(maxCount - 1));
                if (current.distanceSq > maxDistSqInResult) {
                    break;
                }
            }

            if (current.isPoint()) {
                result.add(current.point);
                result.sort(Comparator.comparingDouble(p -> distanceSq(source, p)));
                if (result.size() > maxCount) {
                    ((LinkedList<Point>)result).removeLast(); // 가장 먼 것 제거
                }
            } else { // Node
                if (current.node.isLeaf) {
                    for (Entry e : current.node.entries) {
                        pq.add(new PQEntry(e.point, distanceSq(source, e.point)));
                    }
                } else {
                    for (Entry e : current.node.entries) {
                        pq.add(new PQEntry(e.child, minDistSq(e.mbr, source)));
                    }
                }
            }
        }

        // GUI: (요건 3.5) 탐색된 점들 보여주기
        if (visualizer != null) {
            visualizer.setKNNResults(new ArrayList<>(result));
            visualizer.repaint();
            sleep(TASK_COMPLETION_DELAY);
        }

        return result.iterator();
    }


    // --- Task 4: Delete (노드 제거) ---

    @Override
    public void delete(Point point) {
        // GUI: 작업 시작 알림
        if (visualizer != null) visualizer.setLastOperation("delete", point);

        if (root == null) return;

        Node leaf = findLeaf(root, point);
        if (leaf == null) return; // 지울 포인트 없음

        Entry entryToRemove = null;
        for (Entry e : leaf.entries) {
            if (e.point != null && pointsEqual(e.point, point)) {
                entryToRemove = e;
                break;
            }
        }
        if (entryToRemove == null) return;

        leaf.entries.remove(entryToRemove);
        size--;

        List<Entry> reInsertEntries = new LinkedList<>();
        condenseTree(leaf, reInsertEntries);

        // 재삽입 (Re-insertion)
        for (Entry e : reInsertEntries) {
            // 재삽입 시에는 GUI 지연을 0으로 설정 (삭제가 너무 느려짐)
            if (e.point != null) {
                addForReinsert(e.point);
            }
        }

        // GUI: 갱신 및 시각적 지연
        updateVisualizer(VISUAL_DELAY);
    }

    // 재삽입용 add (GUI 지연 없음)
    private void addForReinsert(Point point) {
        if (root == null) {
            root = new Node(true, null);
            root.entries.add(new Entry(point));
            root.updateMbr();
            size++;
            return;
        }
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


    private Node findLeaf(Node n, Point p) {
        if (n.isLeaf) {
            for(Entry e : n.entries) {
                if(e.point != null && pointsEqual(e.point, p)) {
                    return n;
                }
            }
            return null;
        }

        for (Entry e : n.entries) {
            if (containsPoint(e.mbr, p)) {
                Node result = findLeaf(e.child, p);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void condenseTree(Node n, List<Entry> reInsertList) {
        if (n == root) {
            if (!n.isLeaf && n.entries.size() == 1) { // 루트의 자식이 하나
                root = n.entries.get(0).child;
                root.parent = null;
            } else if (n.isLeaf && n.entries.isEmpty()) {
                root = null; // 트리가 비게 됨
            }
            return;
        }

        if (n.entries.size() < m) { // Underflow
            reInsertList.addAll(n.entries);

            Node parent = n.parent;
            Entry entryToRemove = null;
            for(Entry e : parent.entries) {
                if (e.child == n) {
                    entryToRemove = e;
                    break;
                }
            }
            if (entryToRemove != null) {
                parent.entries.remove(entryToRemove);
            }

            condenseTree(parent, reInsertList);
        } else {
            adjustTree(n, null); // MBR만 갱신
        }
    }


    // --- IsEmpty ---

    @Override
    public boolean isEmpty() {
        return root == null || size == 0;
    }

    // --- GUI 업데이트 및 지연 헬퍼 ---

    private void updateVisualizer(long delayMs) {
        if (visualizer == null) return;

        visualizer.repaint();
        sleep(delayMs);
    }

    private void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    // ####################################################################
    // # GUI 시각화 내부 클래스
    // ####################################################################

    class RTreeVisualizer extends JFrame {
        private RTreePanel panel;

        public RTreeVisualizer(RTreeImpl rtree) {
            this.panel = new RTreePanel(rtree);
            setTitle("4-way R-Tree Visualization");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            add(panel);
            setSize(800, 800); // 윈도우 크기
            setLocationRelativeTo(null); // 화면 중앙에 배치
        }

        // --- Panel 상태 제어 메서드 ---

        public void clearVisuals() { panel.clearVisuals(); }
        public void setLastOperation(String op, Point p) { panel.setLastOperation(op, p); }
        public void setSearchRect(Rectangle r) { panel.setSearchRect(r); }
        public void addPrunedNode(Rectangle r) { panel.addPrunedNode(r); }
        public void addVisitedNode(Rectangle r) { panel.addVisitedNode(r); }
        public void setSearchResults(List<Point> results) { panel.setSearchResults(results); }
        public void setKNNSettings(Point source, int k) { panel.setKNNSettings(source, k); }
        public void addKnnSearchProcess(PQEntry entry) { panel.addKnnSearchProcess(entry); }
        public void setKNNResults(List<Point> results) { panel.setKNNResults(results); }
    }

    class RTreePanel extends JPanel {
        private RTreeImpl rtreeRef;

        // --- 시각화 상태 변수 ---
        private String lastOp = "";
        private Point lastPoint = null;

        // Task 2: Search
        private Rectangle searchRect = null;
        private List<Rectangle> prunedNodes = new ArrayList<>();
        private List<Rectangle> visitedNodes = new ArrayList<>();
        private List<Point> searchResults = new ArrayList<>();

        // Task 3: KNN
        private Point knnSource = null;
        private int kCount = 0;
        private List<PQEntry> knnSearchProcess = new ArrayList<>();
        private List<Point> knnResults = new ArrayList<>();

        // MBR 레벨별 색상
        private static final Color[] LEVEL_COLORS = {
                new Color(255, 0, 0, 150), // Level 0 (Root)
                new Color(0, 0, 255, 100), // Level 1
                new Color(0, 150, 0, 80),  // Level 2
                new Color(255, 128, 0, 60), // Level 3
                new Color(128, 0, 128, 40)  // Level 4+
        };

        public RTreePanel(RTreeImpl rtree) {
            this.rtreeRef = rtree;
            setBackground(Color.WHITE);
        }

        public void clearVisuals() {
            // lastOp와 lastPoint는 유지
            searchRect = null;
            prunedNodes.clear();
            visitedNodes.clear();
            searchResults.clear();
            knnSource = null;
            kCount = 0;
            knnSearchProcess.clear();
            knnResults.clear();
        }

        public void setLastOperation(String op, Point p) { this.lastOp = op; this.lastPoint = p; }
        public void setSearchRect(Rectangle r) { this.searchRect = r; }
        public void addPrunedNode(Rectangle r) { this.prunedNodes.add(r); }
        public void addVisitedNode(Rectangle r) { this.visitedNodes.add(r); }
        public void setSearchResults(List<Point> results) { this.searchResults = new ArrayList<>(results); }
        public void setKNNSettings(Point source, int k) { this.knnSource = source; this.kCount = k; }
        public void addKnnSearchProcess(PQEntry entry) { this.knnSearchProcess.add(entry); }
        public void setKNNResults(List<Point> results) { this.knnResults = new ArrayList<>(results); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // --- 좌표계 변환 (수정됨: 3.5배 확대) ---
            g2.translate(50, this.getHeight() - 50);
            // g2.scale(1.5, -1.5); // [기존] 너무 작음
            g2.scale(3.5, -3.5);    // [수정] 화면에 꽉 차게 확대

            // 그리기 순서
            drawSearchProcess(g2);
            drawKnnProcess(g2);

            if (rtreeRef.root != null) {
                drawTree(g2, rtreeRef.root, 0);
            }

            drawSearchRectAndResults(g2);
            drawKnnResults(g2);
            drawLastOperation(g2);
            drawStatus(g2);
        }

        private void drawTree(Graphics2D g, Node n, int level) {
            if (n == null || n.mbr == null) return;

            Color color = LEVEL_COLORS[Math.min(level, LEVEL_COLORS.length - 1)];
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g.fillRect((int)n.mbr.getLeftTop().getX(), (int)n.mbr.getLeftTop().getY(),
                    (int)(n.mbr.getRightBottom().getX() - n.mbr.getLeftTop().getX()),
                    (int)(n.mbr.getRightBottom().getY() - n.mbr.getLeftTop().getY()));
            g.setColor(color.darker());
            g.setStroke(new BasicStroke(level == 0 ? 2 : 1)); // 루트는 굵게
            g.drawRect((int)n.mbr.getLeftTop().getX(), (int)n.mbr.getLeftTop().getY(),
                    (int)(n.mbr.getRightBottom().getX() - n.mbr.getLeftTop().getX()),
                    (int)(n.mbr.getRightBottom().getY() - n.mbr.getLeftTop().getY()));

            if (n.isLeaf) {
                g.setColor(Color.BLACK);
                for (Entry e : n.entries) {
                    g.fillOval((int)e.point.getX() - 2, (int)e.point.getY() - 2, 4, 4);
                }
            } else {
                for (Entry e : n.entries) {
                    drawTree(g, e.child, level + 1);
                }
            }
        }

        private void drawSearchProcess(Graphics2D g) {
            // Pruned (Gray)
            g.setColor(new Color(150, 150, 150, 50));
            for (Rectangle r : prunedNodes) {
                g.fillRect((int)r.getLeftTop().getX(), (int)r.getLeftTop().getY(),
                        (int)(r.getRightBottom().getX() - r.getLeftTop().getX()),
                        (int)(r.getRightBottom().getY() - r.getLeftTop().getY()));
            }
            // Visited (Cyan)
            g.setColor(new Color(0, 150, 255, 50));
            for (Rectangle r : visitedNodes) {
                g.fillRect((int)r.getLeftTop().getX(), (int)r.getLeftTop().getY(),
                        (int)(r.getRightBottom().getX() - r.getLeftTop().getX()),
                        (int)(r.getRightBottom().getY() - r.getLeftTop().getY()));
            }
        }

        private void drawKnnProcess(Graphics2D g) {
            g.setColor(new Color(0, 200, 0, 30));
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2f, 2f}, 0));
            for (PQEntry entry : knnSearchProcess) {
                if (entry.node != null) {
                    Rectangle r = entry.node.mbr;
                    g.drawRect((int)r.getLeftTop().getX(), (int)r.getLeftTop().getY(),
                            (int)(r.getRightBottom().getX() - r.getLeftTop().getX()),
                            (int)(r.getRightBottom().getY() - r.getLeftTop().getY()));
                }
            }
            g.setStroke(new BasicStroke(1));
        }

        private void drawSearchRectAndResults(Graphics2D g) {
            if (searchRect == null) return;

            g.setColor(Color.BLUE);
            g.setStroke(new BasicStroke(3));
            g.drawRect((int)searchRect.getLeftTop().getX(), (int)searchRect.getLeftTop().getY(),
                    (int)(searchRect.getRightBottom().getX() - searchRect.getLeftTop().getX()),
                    (int)(searchRect.getRightBottom().getY() - searchRect.getLeftTop().getY()));

            g.setColor(Color.YELLOW.darker());
            g.setStroke(new BasicStroke(2));
            for (Point p : searchResults) {
                g.drawOval((int)p.getX() - 5, (int)p.getY() - 5, 10, 10);
            }
        }

        private void drawKnnResults(Graphics2D g) {
            if (knnSource == null) return;

            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(3));
            g.drawLine((int)knnSource.getX() - 6, (int)knnSource.getY() - 6, (int)knnSource.getX() + 6, (int)knnSource.getY() + 6);
            g.drawLine((int)knnSource.getX() - 6, (int)knnSource.getY() + 6, (int)knnSource.getX() + 6, (int)knnSource.getY() - 6);

            g.setColor(Color.YELLOW.darker());
            g.setStroke(new BasicStroke(2));
            for (Point p : knnResults) {
                g.drawOval((int)p.getX() - 5, (int)p.getY() - 5, 10, 10);
            }
        }

        private void drawLastOperation(Graphics2D g) {
            if (lastPoint == null) return;

            if ("add".equals(lastOp)) g.setColor(Color.GREEN.darker());
            else if ("delete".equals(lastOp)) g.setColor(Color.RED);
            else return;

            g.setStroke(new BasicStroke(3));
            g.drawOval((int)lastPoint.getX() - 8, (int)lastPoint.getY() - 8, 16, 16);
        }

        private void drawStatus(Graphics2D g) {
            String status = "";
            if ("add".equals(lastOp)) status = "Task 1: ADD " + lastPoint;
            else if ("search".equals(lastOp)) status = "Task 2: SEARCH " + searchRect;
            else if ("knn".equals(lastOp)) status = "Task 3: KNN (Source=" + knnSource + ", k=" + kCount + ")";
            else if ("delete".equals(lastOp)) status = "Task 4: DELETE " + lastPoint;

            g.scale(1.0, -1.0);
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(12f));
            g.drawString(status, 10, - (int)(getHeight() / 3.5 - 80) ); // Scale이 커졌으므로 텍스트 위치 조정
        }
    }
}