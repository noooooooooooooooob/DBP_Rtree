package org.dfpl.dbp.rtree.team2;

import java.util.List;

/**
 * R-Tree의 노드를 표현하는 클래스
 * 리프 노드: points != null, children == null
 * 내부 노드: points == null, children != null
 */
public class Node {
    // 이 노드와 모든 자손들을 포함하는 최소 경계 사각형 (Minimum Bounding Rectangle)
    private Rectangle mbr;

    // 부모 노드 참조 (루트는 null)
    private Node parent;

    // 노드의 레벨 (루트가 0)
    private int level;

    // 자식 노드들 (내부 노드만 사용, 리프 노드는 null)
    private List<Node> children;

    // 저장된 Point들 (리프 노드만 사용, 내부 노드는 null)
    private List<Point> points;

    public Node(Rectangle mbr, Node parent, List<Node> children, List<Point> points, int level) {
        this.mbr = mbr;
        this.parent = parent;
        this.children = children;
        this.points = points;
        this.level = level;
    }
    
    // 노드 생성용 함수
    public static Node createLeaf(Rectangle mbr, Node parent, List<Point> points, int level) {
        return new Node(mbr, parent, null, points, level);
    }
    public static Node createInternal(Rectangle mbr, Node parent, List<Node> children, int level) {
        return new Node(mbr, parent, children, null, level);
    }

    public void setChildren(List<Node> children) {
        this.children = children;
    }

    // points가 null이 아니면 리프 노드
    public boolean isLeaf() {
        return points != null;
    }

    public Rectangle getMbr() {
        return mbr;
    }

    public void setMbr(Rectangle mbr) {
        this.mbr = mbr;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public List<Node> getChildren() {
        return children;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}

