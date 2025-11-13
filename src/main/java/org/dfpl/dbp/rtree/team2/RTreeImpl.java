package org.dfpl.dbp.rtree.team2;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 4-way R-Tree (M=4, m=2) with Swing visualization (snapshot + step view)
 * - Insert: min-enlargement, quadratic split, path/split visual steps
 * - Search: visited/pruned step view
 * - kNN: best-first with step view
 * - Delete: CondenseTree + reinsertion with underflow/reinsert visual steps
 *
 * JVM options:
 *   -Drtree.stepDelay=180  // step redraw delay(ms); 0 = instant
 *   -Drtree.slow=1         // initial update() small delay
 */
public class RTreeImpl implements RTree {

    // ---------- R-Tree parameters ----------
    private static final int MAX_ENTRIES = 4; // M
    private static final int MIN_ENTRIES = 2; // m

    // ---------- Internal geometry ----------
    private static class DPoint {
        final double x, y;
        DPoint(double x,double y){ this.x=x; this.y=y; }
    }
    private static class DRect {
        double xmin, ymin, xmax, ymax;
        DRect(double xmin,double ymin,double xmax,double ymax){
            this.xmin=xmin; this.ymin=ymin; this.xmax=xmax; this.ymax=ymax;
        }
        DRect copy(){ return new DRect(xmin,ymin,xmax,ymax); }
        double area(){ return Math.max(0,xmax-xmin) * Math.max(0,ymax-ymin); }
        void include(DRect r){
            xmin = Math.min(xmin, r.xmin);
            ymin = Math.min(ymin, r.ymin);
            xmax = Math.max(xmax, r.xmax);
            ymax = Math.max(ymax, r.ymax);
        }
        void include(DPoint p){
            xmin = Math.min(xmin, p.x);
            ymin = Math.min(ymin, p.y);
            xmax = Math.max(xmax, p.x);
            ymax = Math.max(ymax, p.y);
        }
        boolean intersects(DRect r){
            return !(r.xmin > xmax || r.xmax < xmin || r.ymin > ymax || r.ymax < ymin);
        }
        boolean contains(DPoint p){
            return p.x >= xmin && p.x <= xmax && p.y >= ymin && p.y <= ymax;
        }
        static double enlargement(DRect a, DRect b){
            double nx1 = Math.min(a.xmin, b.xmin);
            double ny1 = Math.min(a.ymin, b.ymin);
            double nx2 = Math.max(a.xmax, b.xmax);
            double ny2 = Math.max(a.ymax, b.ymax);
            double newA = Math.max(0, nx2-nx1) * Math.max(0, ny2-ny1);
            return newA - a.area();
        }
        static double mindist(DRect r, DPoint p){
            double dx = (p.x < r.xmin) ? r.xmin - p.x : (p.x > r.xmax ? p.x - r.xmax : 0);
            double dy = (p.y < r.ymin) ? r.ymin - p.y : (p.y > r.ymax ? p.y - r.ymax : 0);
            return Math.hypot(dx, dy);
        }
    }

    // ---------- Tree structures ----------
    private static final AtomicLong NODE_IDS = new AtomicLong(1);

    private static class Entry {
        DRect mbr;
        Node child;      // null if leaf
        Point userPoint; // set if leaf
        Entry(DRect m, Node c){ mbr=m; child=c; }
        Entry(DRect m, Point p){ mbr=m; userPoint=p; }
    }

    private static class Node {
        final long id = NODE_IDS.getAndIncrement();
        boolean isLeaf;
        ArrayList<Entry> entries = new ArrayList<>();
        Node parent;
        DRect mbr;
        Node(boolean leaf){ isLeaf = leaf; }
        void recompute(){
            if (entries.isEmpty()){ mbr = null; return; }
            DRect agg = entries.get(0).mbr.copy();
            for (int i=1;i<entries.size();i++) agg.include(entries.get(i).mbr);
            mbr = agg;
        }
    }

    private Node root = new Node(true);
    private int size = 0;

    // ---------- Adapters to user types ----------
    private static DRect rectFrom(Rectangle r){
        Point lt = r.getLeftTop();
        Point rb = r.getRightBottom();
        double x1 = lt.getX(), y1 = lt.getY();
        double x2 = rb.getX(), y2 = rb.getY();
        double xmin = Math.min(x1,x2), ymin = Math.min(y1,y2);
        double xmax = Math.max(x1,x2), ymax = Math.max(y1,y2);
        return new DRect(xmin,ymin,xmax,ymax);
    }
    private static DRect rectFrom(Point p){
        return new DRect(p.getX(), p.getY(), p.getX(), p.getY());
    }
    private static boolean same(Point a, Point b){
        return Double.compare(a.getX(), b.getX())==0 && Double.compare(a.getY(), b.getY())==0;
    }

    // ---------- Visualization (Snapshot-based) ----------
    private final Visual visual = new Visual();

    private static class VisualFrame extends JFrame {
        VisualFrame(){
            super("R-Tree Visualizer (4-way)");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(820, 820);
            setLocationByPlatform(true);
        }
    }

    private class Visual extends JPanel {
        private final VisualFrame frame;

        // ----- Snapshot node -----
        private class SNode {
            long id;
            boolean isLeaf;
            DRect mbr;
            List<SNode> children = new ArrayList<>();
            List<DPoint> points = new ArrayList<>(); // leaf points
        }

        // snapshot root
        SNode snapRoot;

        // world bounds for auto-scale
        double wx1=0, wy1=0, wx2=200, wy2=200;

        // marks
        DRect lastQuery;
        DPoint lastSource;
        Set<Long> visited = Collections.synchronizedSet(new HashSet<>());
        Set<Long> pruned  = Collections.synchronizedSet(new HashSet<>());
        Point lastInserted, lastDeleted;
        List<Point> knnSoFar = new ArrayList<>();

        Visual(){
            frame = new VisualFrame();
            frame.setContentPane(this);
            frame.setVisible(true);
        }

        void resetMarks(){
            lastQuery=null; lastSource=null; visited.clear(); pruned.clear();
            lastInserted=null; lastDeleted=null; knnSoFar.clear();
        }

        // snapshot builder
        private SNode snap(Node n){
            if (n==null) return null;
            SNode s = new SNode();
            s.id = n.id;
            s.isLeaf = n.isLeaf;
            s.mbr = (n.mbr==null)? null : n.mbr.copy();
            if (n.isLeaf){
                for (Entry e : new ArrayList<>(n.entries)){
                    s.points.add(new DPoint(e.mbr.xmin, e.mbr.ymin));
                }
            } else {
                for (Entry e : new ArrayList<>(n.entries)){
                    s.children.add(snap(e.child));
                }
            }
            return s;
        }

        // initial snapshot once per operation
        void update(Node r){
            this.snapRoot = snap(r);
            autoFitSnapshot(this.snapRoot);
            repaintSlow(); // optional slow
        }
        // Visual 내부 필드에 추가
        private volatile int stepDelay = Integer.getInteger("rtree.stepDelay", 60);

        // 기존 stepDelayMs() 대신 사용
        private int stepDelayMs(){ return stepDelay; }

        // 현재 지연을 바꾸는 헬퍼
        void setStepDelay(int ms){
            stepDelay = Math.max(0, ms);
            frame.setTitle("R-Tree Visualizer (delay="+ stepDelay +"ms)");
        }

        // step redraw for process view
        void redraw(){
            repaint();
            int d = stepDelayMs();
            if (d > 0){ try { Thread.sleep(d); } catch (InterruptedException ignored) {} }
        }

        void setQuery(DRect q){ lastQuery=q; }
        void setSource(DPoint s){ lastSource=s; }
        void setLastInserted(Point p){ lastInserted=p; }
        void setLastDeleted(Point p){ lastDeleted=p; }
        void setKnn(List<Point> l){ knnSoFar = new ArrayList<>(l); }
        void markVisited(Node n){ if(n!=null) visited.add(n.id); }
        void markPruned(Node n){ if(n!=null) pruned.add(n.id); }

        // ---- NEW: step helpers for add/delete process ----
        void showPath(List<Node> path){
            if (path==null || path.isEmpty()) return;
            for (Node n : path){ markVisited(n); redraw(); }
        }
        void flashSplit(Node left, Node right){
            if (left==null && right==null) return;
            for (int i=0;i<2;i++){
                if (left!=null) markVisited(left);
                if (right!=null) markVisited(right);
                redraw();
                if (left!=null) pruned.add(left.id);
                if (right!=null) pruned.add(right.id);
                redraw();
                if (left!=null){ visited.remove(left.id); pruned.remove(left.id); }
                if (right!=null){ visited.remove(right.id); pruned.remove(right.id); }
            }
        }
        void flashUnderflow(Node n){
            if (n==null) return;
            for (int i=0;i<2;i++){
                markPruned(n); redraw();
                pruned.remove(n.id); redraw();
            }
        }
        void flashReinsert(Point p){
            setLastInserted(p); redraw();
            setLastInserted(null); redraw();
        }

        private void autoFitSnapshot(SNode r){
            if (r==null || r.mbr==null) return;
            wx1=r.mbr.xmin; wy1=r.mbr.ymin; wx2=r.mbr.xmax; wy2=r.mbr.ymax;
            double dx=(wx2-wx1)*0.05+1, dy=(wy2-wy1)*0.05+1;
            wx1-=dx; wy1-=dy; wx2+=dx; wy2+=dy;
        }

        private java.awt.Point map(double x, double y, int W, int H){
            double nx = (x - wx1) / Math.max(1e-9,(wx2-wx1));
            double ny = (y - wy1) / Math.max(1e-9,(wy2-wy1));
            int sx = (int)Math.round(40 + nx*(W-80));
            int sy = (int)Math.round(H-40 - ny*(H-80)); // top is +Y
            return new java.awt.Point(sx, sy);
        }

        private java.awt.Rectangle toAwtRect(DRect r, int W, int H){
            java.awt.Point a = map(r.xmin,r.ymin,W,H);
            java.awt.Point b = map(r.xmax,r.ymax,W,H);
            int x=Math.min(a.x,b.x), y=Math.min(a.y,b.y);
            int w=Math.abs(a.x-b.x), h=Math.abs(a.y-b.y);
            return new java.awt.Rectangle(x,y,w,h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W=getWidth(), H=getHeight();

            g2.setColor(Color.white); g2.fillRect(0,0,W,H);
            g2.setColor(Color.lightGray); g2.drawRect(20,20,W-40,H-40);

            if (snapRoot != null){
                drawNode(g2, snapRoot, 0, W, H);
            }

            if (lastQuery != null){
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(new Color(220,0,0,180));
                java.awt.Rectangle rr = toAwtRect(lastQuery, W,H);
                g2.draw(rr);
                g2.setStroke(new BasicStroke(1f));
            }

            if (lastSource != null){
                java.awt.Point ps = map(lastSource.x,lastSource.y,W,H);
                g2.setColor(new Color(0,120,255));
                int r=6; g2.fillOval(ps.x-r, ps.y-r, 2*r, 2*r);
                g2.drawString("source", ps.x+8, ps.y-8);
            }

            if (lastInserted != null){
                java.awt.Point p = map(lastInserted.getX(), lastInserted.getY(), W,H);
                g2.setColor(new Color(0,170,0));
                int r=6; g2.fillOval(p.x-r,p.y-r,2*r,2*r);
                g2.drawString("inserted", p.x+8,p.y-8);
            }
            if (lastDeleted != null){
                java.awt.Point p = map(lastDeleted.getX(), lastDeleted.getY(), W,H);
                g2.setColor(new Color(190,0,190));
                int r=6; g2.fillOval(p.x-r,p.y-r,2*r,2*r);
                g2.drawString("deleted", p.x+8,p.y-8);
            }
        }

        private void drawNode(Graphics2D g2, SNode n, int depth, int W, int H){
            Color[] pal = {
                    new Color(0,0,0,50), new Color(0,120,255,60),
                    new Color(0,170,0,60), new Color(255,140,0,60),
                    new Color(190,0,190,60)
            };
            Color c = pal[depth % pal.length];

            if (n.mbr != null){
                java.awt.Rectangle rr = toAwtRect(n.mbr, W, H);
                g2.setColor(c); g2.fill(rr);
                if (pruned.contains(n.id)) g2.setColor(new Color(180,180,180));
                else if (visited.contains(n.id)) g2.setColor(Color.red);
                else g2.setColor(Color.darkGray);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(rr);
            }

            if (n.isLeaf){
                g2.setColor(Color.black);
                for (DPoint p : n.points){
                    java.awt.Point sp = map(p.x,p.y,W,H);
                    int d=4; g2.fillOval(sp.x-d, sp.y-d, 2*d, 2*d);
                }
            } else {
                for (SNode ch : n.children){
                    drawNode(g2, ch, depth+1, W, H);
                }
            }
        }

        private void repaintSlow(){
            repaint();
            if ("1".equals(System.getProperty("rtree.slow","0"))){
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ---------- ctor ----------
    public RTreeImpl(){
        visual.update(root); // show empty tree
    }

    // ---------- Public API (with process view) ----------
    @Override
    public void add(Point point) {
        if (contains(point)) return;

        // 초기 스냅샷 + 마킹 초기화
        visual.resetMarks();
        visual.update(root);

        // 경로 추적 + 표시
        List<Node> path = new ArrayList<>();
        Node leaf = chooseLeafTrace(root, rectFrom(point), path);
        visual.showPath(path);

        // 삽입
        leaf.entries.add(new Entry(rectFrom(point), point));
        leaf.recompute();

        // 분할 체크
        Node split = null;
        if (leaf.entries.size() > MAX_ENTRIES){
            split = splitNode(leaf);
            visual.flashSplit(leaf, split);
        }

        // 위로 올라가며 조정(단계 표시)
        Node cur = leaf, nn = split;
        while(true){
            if (cur.parent == null){
                if (nn != null){
                    Node newRoot = new Node(false);
                    newRoot.entries.add(new Entry(cur.mbr.copy(), cur));
                    newRoot.entries.add(new Entry(nn.mbr.copy(), nn));
                    cur.parent = newRoot; nn.parent = newRoot;
                    newRoot.recompute();
                    root = newRoot;
                    visual.markVisited(newRoot); visual.redraw();
                } else {
                    cur.recompute();
                    root = cur;
                }
                break;
            }
            Node p = cur.parent;
            for (Entry e : p.entries) if (e.child==cur){ e.mbr = cur.mbr.copy(); break; }
            p.recompute();
            visual.markVisited(p); visual.redraw();

            if (nn != null){
                p.entries.add(new Entry(nn.mbr.copy(), nn));
                nn.parent = p;
                if (p.entries.size() > MAX_ENTRIES){
                    Node ps = splitNode(p);
                    visual.flashSplit(p, ps);
                    cur = p; nn = ps;
                } else { cur = p; nn = null; }
            } else { cur = p; }
        }

        size++;
        visual.setLastInserted(point);
        visual.update(root);
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        visual.resetMarks();
        DRect query = rectFrom(rectangle);
        visual.setQuery(query);

        // 시작 시 1회 스냅샷
        visual.update(root);

        ArrayList<Point> results = new ArrayList<>();
        Deque<Node> dq = new ArrayDeque<>();
        dq.add(root);
        while(!dq.isEmpty()){
            Node n = dq.pollFirst();

            if (n==null || n.mbr==null){
                visual.markPruned(n);
                visual.redraw();
                continue;
            }
            if (!n.mbr.intersects(query)){
                visual.markPruned(n);
                visual.redraw();
                continue;
            }

            visual.markVisited(n);
            visual.redraw();

            if (n.isLeaf){
                for (Entry e : n.entries){
                    if (query.contains(new DPoint(e.mbr.xmin, e.mbr.ymin))){
                        results.add(e.userPoint);
                    }
                }
            } else {
                for (Entry e : n.entries){
                    if (e.child!=null && e.child.mbr!=null && e.child.mbr.intersects(query)){
                        dq.addLast(e.child);
                    } else {
                        visual.markPruned(e.child);
                        visual.redraw();
                    }
                }
            }
        }
        return results.iterator();
    }

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        visual.resetMarks();
        DPoint s = new DPoint(source.getX(), source.getY());
        visual.setSource(s);

        // 시작 시 1회 스냅샷
        visual.update(root);

        PriorityQueue<Object[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> (double)a[0]));
        pq.add(new Object[]{ DRect.mindist(root.mbr==null? new DRect(0,0,0,0):root.mbr, s), 0, root }); // 0=node, 1=entry

        ArrayList<Point> knn = new ArrayList<>();
        while(!pq.isEmpty() && knn.size()<maxCount){
            Object[] it = pq.poll();
            int type = (int) it[1];

            if (type==0){
                Node n = (Node) it[2];
                if (n==null || n.mbr==null) continue;

                visual.markVisited(n);
                visual.redraw(); // pop

                if (n.isLeaf){
                    for (Entry e : n.entries){
                        double d = Math.hypot(s.x - e.mbr.xmin, s.y - e.mbr.ymin);
                        pq.add(new Object[]{ d, 1, e });
                    }
                    visual.redraw();
                } else {
                    for (Entry e : n.entries){
                        if (e.child!=null && e.child.mbr!=null){
                            double d = DRect.mindist(e.child.mbr, s);
                            pq.add(new Object[]{ d, 0, e.child });
                        }
                    }
                    visual.redraw();
                }
            } else {
                Entry e = (Entry) it[2];
                knn.add(e.userPoint);
                visual.setKnn(knn);
                visual.redraw(); // confirm NN
            }
        }

        return new Iterator<>() {
            int i=0;
            @Override public boolean hasNext(){ return i<knn.size(); }
            @Override public Point next(){
                if (!hasNext()) throw new NoSuchElementException();
                Point p = knn.get(i++);
                visual.setKnn(knn.subList(0,i));
                visual.redraw();
                return p;
            }
        };
    }

    @Override
    public void delete(Point point) {
        // 초기 스냅샷 + 마킹 초기화
        visual.resetMarks();
        visual.update(root);

        // 리프 찾기 + 경로 표시
        List<Node> path = new ArrayList<>();
        Node leaf = findLeafTrace(root, point, path);
        if (leaf==null) return;
        visual.showPath(path);

        // 리프에서 엔트리 제거
        Entry t=null;
        for (Entry e : leaf.entries) if (same(e.userPoint, point)){ t=e; break; }
        if (t==null) return;
        leaf.entries.remove(t);
        size--;

        // CondenseTree with viz
        ArrayList<Node> reinsertSubtrees = new ArrayList<>();
        Node cur = leaf;
        while (cur != null){
            if (cur != root && cur.entries.size() < MIN_ENTRIES){
                visual.flashUnderflow(cur);

                Node parent = cur.parent;
                Entry link=null; for (Entry e : parent.entries) if (e.child==cur){ link=e; break; }
                if (link!=null) parent.entries.remove(link);

                if (cur.isLeaf){
                    for (Entry e : cur.entries){
                        // 재삽입 시각화
                        visual.flashReinsert(e.userPoint);
                        List<Node> rPath = new ArrayList<>();
                        Node where = chooseLeafTrace(root, rectFrom(e.userPoint), rPath);
                        visual.showPath(rPath);
                        // 실제 재삽입
                        where.entries.add(new Entry(rectFrom(e.userPoint), e.userPoint));
                        where.recompute();
                        Node sp = null;
                        if (where.entries.size()>MAX_ENTRIES){
                            sp = splitNode(where);
                            visual.flashSplit(where, sp);
                        }
                        adjustTree(where, sp);
                    }
                } else {
                    for (Entry e : cur.entries) reinsertSubtrees.add(e.child);
                }
                cur.entries.clear();
                parent.recompute();
                visual.markVisited(parent); visual.redraw();
                cur = parent;
            } else {
                cur.recompute();
                visual.markVisited(cur); visual.redraw();
                cur = cur.parent;
            }
        }
        for (Node s : reinsertSubtrees) collectReinsertWithViz(s);

        // 루트 수축
        while (!root.isLeaf && root.entries.size()==1){
            root = root.entries.get(0).child;
            root.parent = null;
            visual.markVisited(root); visual.redraw();
        }
        root.recompute();

        visual.setLastDeleted(point);
        visual.update(root);
    }

    @Override
    public boolean isEmpty() {
        return size==0;
    }

    // ---------- Core algorithms ----------
    private Node chooseLeaf(Node n, DRect m){
        if (n.isLeaf) return n;
        Entry best=null; double bestEnl=Double.POSITIVE_INFINITY, bestArea=Double.POSITIVE_INFINITY;
        for (Entry e : n.entries){
            double enl = DRect.enlargement(e.mbr, m), area = e.mbr.area();
            if (enl < bestEnl || (enl==bestEnl && area < bestArea)){
                best = e; bestEnl = enl; bestArea = area;
            }
        }
        return chooseLeaf(best.child, m);
    }

    // chooseLeaf with path trace (for visualization)
    private Node chooseLeafTrace(Node n, DRect m, List<Node> path){
        path.add(n);
        if (n.isLeaf) return n;
        Entry best=null; double bestEnl=Double.POSITIVE_INFINITY, bestArea=Double.POSITIVE_INFINITY;
        for (Entry e : n.entries){
            double enl = DRect.enlargement(e.mbr, m), area = e.mbr.area();
            if (enl < bestEnl || (enl==bestEnl && area < bestArea)){
                best = e; bestEnl = enl; bestArea = area;
            }
        }
        return chooseLeafTrace(best.child, m, path);
    }

    private Node splitNode(Node n){
        ArrayList<Entry> E = new ArrayList<>(n.entries);
        n.entries.clear();

        // pick seeds (quadratic)
        int i1=-1,i2=-1; double worst=-1;
        for(int i=0;i<E.size();i++){
            for(int j=i+1;j<E.size();j++){
                double d = DRect.enlargement(E.get(i).mbr, E.get(j).mbr);
                if (d > worst){ worst=d; i1=i; i2=j; }
            }
        }
        Entry a = E.remove(i2);
        Entry b = E.remove(i1);

        Node g1 = n;
        Node g2 = new Node(n.isLeaf); g2.parent = n.parent;

        g1.entries.add(b); g1.recompute();
        g2.entries.add(a); g2.recompute();

        while(!E.isEmpty()){
            if (g1.entries.size() + E.size() == MIN_ENTRIES){ g1.entries.addAll(E); E.clear(); g1.recompute(); break; }
            if (g2.entries.size() + E.size() == MIN_ENTRIES){ g2.entries.addAll(E); E.clear(); g2.recompute(); break; }

            Entry pick=null; double diff=-1;
            for (Entry cand : E){
                double d1 = DRect.enlargement(g1.mbr, cand.mbr);
                double d2 = DRect.enlargement(g2.mbr, cand.mbr);
                double dv = Math.abs(d1 - d2);
                if (dv > diff){ diff=dv; pick=cand; }
            }
            E.remove(pick);
            double inc1 = DRect.enlargement(g1.mbr, pick.mbr);
            double inc2 = DRect.enlargement(g2.mbr, pick.mbr);
            if (inc1 < inc2 || (inc1==inc2 && g1.mbr.area()<g2.mbr.area())){
                g1.entries.add(pick); g1.recompute();
            } else {
                g2.entries.add(pick); g2.recompute();
            }
        }

        if (!n.isLeaf){
            for (Entry e : g1.entries) if (e.child!=null) e.child.parent=g1;
            for (Entry e : g2.entries) if (e.child!=null) e.child.parent=g2;
        }
        return g2;
    }

    private void adjustTree(Node n, Node nn){
        Node cur = n, split = nn;
        while(true){
            if (cur.parent == null){
                if (split != null){
                    Node newRoot = new Node(false);
                    newRoot.entries.add(new Entry(cur.mbr.copy(), cur));
                    newRoot.entries.add(new Entry(split.mbr.copy(), split));
                    cur.parent = newRoot; split.parent = newRoot;
                    newRoot.recompute();
                    root = newRoot;
                } else {
                    cur.recompute();
                    root = cur;
                }
                return;
            }
            Node p = cur.parent;
            for (Entry e : p.entries) if (e.child==cur){ e.mbr = cur.mbr.copy(); break; }
            p.recompute();

            if (split != null){
                p.entries.add(new Entry(split.mbr.copy(), split));
                split.parent = p;
                if (p.entries.size() > MAX_ENTRIES){
                    Node ps = splitNode(p);
                    cur = p; split = ps;
                } else {
                    cur = p; split = null;
                }
            } else {
                cur = p;
            }
        }
    }

    private Node findLeaf(Node n, Point p){
        if (n.isLeaf){
            for (Entry e : n.entries) if (same(e.userPoint, p)) return n;
            return null;
        }
        DRect pr = rectFrom(p);
        for (Entry e : n.entries){
            if (e.child!=null && e.child.mbr!=null &&
                    (e.child.mbr.contains(new DPoint(pr.xmin,pr.ymin)) || e.child.mbr.intersects(pr))){
                Node f = findLeaf(e.child, p);
                if (f!=null) return f;
            }
        }
        return null;
    }

    // find leaf with path trace (for delete viz)
    private Node findLeafTrace(Node n, Point p, List<Node> path){
        path.add(n);
        if (n.isLeaf){
            for (Entry e : n.entries) if (same(e.userPoint, p)) return n;
            return null;
        }
        DRect pr = rectFrom(p);
        for (Entry e : n.entries){
            if (e.child!=null && e.child.mbr!=null &&
                    (e.child.mbr.contains(new DPoint(pr.xmin,pr.ymin)) || e.child.mbr.intersects(pr))){
                Node f = findLeafTrace(e.child, p, path);
                if (f!=null) return f;
            }
        }
        return null;
    }

    private void condenseTree(Node n){
        ArrayList<Node> reinsertSubtrees = new ArrayList<>();
        Node cur = n;
        while(cur != null){
            if (cur != root && cur.entries.size() < MIN_ENTRIES){
                Node parent = cur.parent;
                Entry link=null; for (Entry e : parent.entries) if (e.child==cur){ link=e; break; }
                if (link!=null) parent.entries.remove(link);

                if (cur.isLeaf){
                    for (Entry e : cur.entries) reinsertPoint(e.userPoint);
                } else {
                    for (Entry e : cur.entries) reinsertSubtrees.add(e.child);
                }
                cur.entries.clear();
                parent.recompute();
                cur = parent;
            } else {
                cur.recompute();
                cur = cur.parent;
            }
        }
        for (Node s : reinsertSubtrees) collectReinsert(s);
    }

    private void collectReinsert(Node n){
        if (n.isLeaf){
            for (Entry e : n.entries) reinsertPoint(e.userPoint);
        } else {
            for (Entry e : n.entries) collectReinsert(e.child);
        }
    }

    // delete용: 서브트리 재삽입 + 과정 표시
    private void collectReinsertWithViz(Node n){
        if (n.isLeaf){
            for (Entry e : n.entries){
                visual.flashReinsert(e.userPoint);
                List<Node> rPath = new ArrayList<>();
                Node where = chooseLeafTrace(root, rectFrom(e.userPoint), rPath);
                visual.showPath(rPath);
                where.entries.add(new Entry(rectFrom(e.userPoint), e.userPoint));
                where.recompute();
                Node sp=null; if (where.entries.size()>MAX_ENTRIES){ sp = splitNode(where); visual.flashSplit(where, sp); }
                adjustTree(where, sp);
            }
        } else {
            for (Entry e : n.entries) collectReinsertWithViz(e.child);
        }
    }

    private void reinsertPoint(Point p){
        Node leaf = chooseLeaf(root, rectFrom(p));
        leaf.entries.add(new Entry(rectFrom(p), p));
        leaf.recompute();
        Node sp = null;
        if (leaf.entries.size() > MAX_ENTRIES) sp = splitNode(leaf);
        adjustTree(leaf, sp);
    }

    private boolean contains(Point p){ return findLeaf(root, p) != null; }
}
