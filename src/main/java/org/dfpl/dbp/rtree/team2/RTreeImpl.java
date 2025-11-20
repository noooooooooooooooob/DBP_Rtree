package org.dfpl.dbp.rtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public class RTreeImpl implements RTree {

    // 요건 4-way R-Tree로 구현한다.
    // Maven Project로 만든다.
    // 기존의 R-Tree를 활용하지 않는다.
    // 여러분의 프로젝트에는 최소한의 dependency가 포함되어 있어야 함.
    // 멤버 변수의 활용은 어느정도 자유로움
    // 단, R-Tree 구현이어야 하고, 요행을 바라지 않는다.

    private static final int MAX_CHILDREN = 4;
    private static final int MIN_CHILDREN = MAX_CHILDREN / 2;

    private Node root = null;

    public Node getRoot() {
        return root;
    }

    private RTreeListener listener = null;

    public void setRTreeListener(RTreeListener listener) {
        this.listener = listener;
    }

    @Override
    public void add(Point point) {
        if (root == null) {
            // 트리가 비어있는 경우, 새로운 루트 노드 생성
            // Point 하나만 포함하는 Rectangle을 MBR로 설정
            // 루트의 레벨은 항상 0
            ArrayList<Point> initialPoints = new ArrayList<>();
            initialPoints.add(point);
            root = Node.createLeaf(new Rectangle(point, point), null, initialPoints, 0);
            return;
        }

        if (contains(point) != null) {
            // 이미 트리에 존재하는 포인트는 삽입하지 않음
            return;
        }

        // 노드를 넣을 적절한 리프 노드 찾기
        // MBR 확장이 가장 적은 경로를 따라 내려감
        Node leafNode = chooseLeaf(root, point);

        // 리프 노드에 포인트 추가
        List<Point> points = leafNode.getPoints();
        points.add(point);

        // 노드가 초과되었는지 확인 (MAX_CHILDREN 초과)
        if (points.size() > MAX_CHILDREN) {
            // 초과되었으면 노드를 두 개로 분할
            // 분할 과정에서 부모의 MBR도 자동으로 업데이트됨
            splitNode(leafNode);
        } else {
            // 초과하지 않았으면 노드들의 MBR만 업데이트
            // 리프에서 루트까지 올라가며 각 노드의 MBR 재계산
            adjustTreeAfterInsertion(leafNode);
        }

        // 리스너 호출
        if (listener != null) {
            listener.onRTreeChanged(this);
        }
    }

    /**
     * 삽입 후 리프부터 루트까지 올라가며 MBR 업데이트
     * 각 레벨에서 모든 자식들의 MBR을 고려하여 부모 MBR 재계산
     */
    private void adjustTreeAfterInsertion(Node node) {
        // 현재 노드의 부모부터 시작
        Node current = node;

        // 루트에 도달할 때까지 반복
        while (current != null) {
            if (current.isLeaf()) {
                // 리프 노드는 points로부터 MBR 재계산
                Rectangle updatedMbr = RectangleBuilder.fromPoints(current.getPoints());
                current.setMbr(updatedMbr);
            } else {
                // 내부 노드는 children으로부터 MBR 재계산
                Rectangle updatedMbr = RectangleBuilder.fromNodes(current.getChildren());
                current.setMbr(updatedMbr);
            }
            current = current.getParent();
        }
    }

    /**
     * 노드를 분할하고 트리를 재구성하는 함수
     * 오버플로우(MAX_CHILDREN 초과)가 발생했을 때 호출됨
     */
    private void splitNode(Node splitNode) {
        if (splitNode.isLeaf()) {
            // === 리프 노드 분할 (최소 면적 분할 적용) ===
            List<Point> points = splitNode.getPoints();
            
            // 최적의 분할을 찾기 위한 변수들
            double minAreaSum = Double.MAX_VALUE;
            int bestAxis = 0; // 0: X축, 1: Y축
            int bestIndex = 0;

            // X축 정렬, Y축 정렬 각각에 대해 (2개, 3개), (3개, 2개) 이렇게 분할을 해보면서 가장 작은 면적 합을 찾음
            
            // X축 기준 검사
            // (2개, 3개), (3개, 2개) 이렇게 분할을 해보면서 가장 작은 면적 합을 찾음
            points.sort((p1, p2) -> Double.compare(p1.getX(), p2.getX()));
            for (int i = MIN_CHILDREN; i <= points.size() - MIN_CHILDREN; i++) {
                Rectangle mbr1 = RectangleBuilder.fromPoints(points.subList(0, i));
                Rectangle mbr2 = RectangleBuilder.fromPoints(points.subList(i, points.size()));
                double areaSum = mbr1.getArea() + mbr2.getArea();
                
                if (areaSum < minAreaSum) {
                    minAreaSum = areaSum;
                    bestAxis = 0;
                    bestIndex = i;
                }
            }
            
            // Y축 기준 검사
            // (2개, 3개), (3개, 2개) 이렇게 분할을 해보면서 가장 작은 면적 합을 찾음
            points.sort((p1, p2) -> Double.compare(p1.getY(), p2.getY()));
            for (int i = MIN_CHILDREN; i <= points.size() - MIN_CHILDREN; i++) {
                Rectangle mbr1 = RectangleBuilder.fromPoints(points.subList(0, i));
                Rectangle mbr2 = RectangleBuilder.fromPoints(points.subList(i, points.size()));
                double areaSum = mbr1.getArea() + mbr2.getArea();
                
                if (areaSum < minAreaSum) {
                    minAreaSum = areaSum;
                    bestAxis = 1;
                    bestIndex = i;
                }
            }
            
            // 가장 작은 면적을 가진 최적의 축으로 다시 정렬하여 분할
            if (bestAxis == 0) {
                points.sort((p1, p2) -> Double.compare(p1.getX(), p2.getX()));
            } else {
                points.sort((p1, p2) -> Double.compare(p1.getY(), p2.getY()));
            }
            
            List<Point> leftPoints = new ArrayList<>(points.subList(0, bestIndex));
            List<Point> rightPoints = new ArrayList<>(points.subList(bestIndex, points.size()));

            // 각 그룹에 대해 새 리프 노드 생성
            // fromPoints: 포인트들을 모두 포함하는 MBR 계산
            // 분할된 리프 노드들은 원래 노드와 동일한 레벨 유지
            int childLevel = splitNode.getLevel();
            Node leftNode = Node.createLeaf(RectangleBuilder.fromPoints(leftPoints), splitNode.getParent(), leftPoints,
                    childLevel);
            Node rightNode = Node.createLeaf(RectangleBuilder.fromPoints(rightPoints), splitNode.getParent(),
                    rightPoints, childLevel);
            
            // 부모 연결 및 트리 구조 업데이트
            if (splitNode.getParent() == null) {
                // === 루트 노드가 분할되는 경우 ===
                ArrayList<Node> rootChildren = new ArrayList<>();
                rootChildren.add(leftNode);
                rootChildren.add(rightNode);
                Rectangle newRootMbr = RectangleBuilder.fromNodes(rootChildren);

                // 새로운 루트 노드 생성 (내부 노드)
                // 두 분할된 노드가 자식이 됨
                Node newRoot = Node.createInternal(newRootMbr, null, rootChildren, 0);

                // 분할된 노드들의 부모를 새 루트로 설정
                leftNode.setParent(newRoot);
                rightNode.setParent(newRoot);

                // 트리의 루트 업데이트
                root = newRoot;

                // 트리가 조정되었으니 레벨 업데이트
                adjustLevelsAfterPromotion(root);
            } else {
                // === 일반 리프 노드가 분할되는 경우 ===
                Node parent = splitNode.getParent();
                List<Node> siblings = parent.getChildren();

                // 부모의 자식 목록에서 분할된 노드 제거
                siblings.remove(splitNode);

                // 두 개의 새 노드를 부모의 자식으로 추가
                siblings.add(leftNode);
                siblings.add(rightNode);

                // 새 노드들의 부모 포인터 설정
                leftNode.setParent(parent);
                rightNode.setParent(parent);
                
                // 부모의 MBR을 모든 자식들을 포함하도록 재계산
                Rectangle newParentMbr = RectangleBuilder.fromNodes(siblings);
                parent.setMbr(newParentMbr);

                // 부모 노드도 초과되었는지 확인
                if (siblings.size() > MAX_CHILDREN) {
                    // 재귀적으로 부모 노드도 분할
                    splitNode(parent);
                } else {
                    // 부모는 괜찮으면 상위 노드들의 MBR만 업데이트
                    adjustTreeAfterInsertion(parent);
                }
            }
        } else {
            // === 내부 노드 분할 (최소 면적 분할 적용) ===
            List<Node> children = splitNode.getChildren();
            
            double minAreaSum = Double.MAX_VALUE;
            int bestAxis = 0;
            int bestIndex = 0;
            
            // X축 기준 (MBR의 중심점 또는 왼쪽 좌표 기준)
            children.sort((n1, n2) -> Double.compare(n1.getMbr().getLeftTop().getX(), n2.getMbr().getLeftTop().getX()));
            for (int i = MIN_CHILDREN; i <= children.size() - MIN_CHILDREN; i++) {
                Rectangle mbr1 = RectangleBuilder.fromNodes(children.subList(0, i));
                Rectangle mbr2 = RectangleBuilder.fromNodes(children.subList(i, children.size()));
                double areaSum = mbr1.getArea() + mbr2.getArea();
                
                if (areaSum < minAreaSum) {
                    minAreaSum = areaSum;
                    bestAxis = 0;
                    bestIndex = i;
                }
            }
            
            // Y축 기준
            children.sort((n1, n2) -> Double.compare(n1.getMbr().getLeftTop().getY(), n2.getMbr().getLeftTop().getY()));
            for (int i = MIN_CHILDREN; i <= children.size() - MIN_CHILDREN; i++) {
                Rectangle mbr1 = RectangleBuilder.fromNodes(children.subList(0, i));
                Rectangle mbr2 = RectangleBuilder.fromNodes(children.subList(i, children.size()));
                double areaSum = mbr1.getArea() + mbr2.getArea();
                
                if (areaSum < minAreaSum) {
                    minAreaSum = areaSum;
                    bestAxis = 1;
                    bestIndex = i;
                }
            }
            
            // 최적 분할 적용
            if (bestAxis == 0) {
                children.sort((n1, n2) -> Double.compare(n1.getMbr().getLeftTop().getX(), n2.getMbr().getLeftTop().getX()));
            } else {
                children.sort((n1, n2) -> Double.compare(n1.getMbr().getLeftTop().getY(), n2.getMbr().getLeftTop().getY()));
            }
            
            List<Node> leftChildren = new ArrayList<>(children.subList(0, bestIndex));
            List<Node> rightChildren = new ArrayList<>(children.subList(bestIndex, children.size()));

            // 각 그룹의 MBR 계산
            Rectangle leftMbr = RectangleBuilder.fromNodes(leftChildren);
            Rectangle rightMbr = RectangleBuilder.fromNodes(rightChildren);

            // 각 그룹에 대해 새 내부 노드 생성
            // 분할된 내부 노드들은 원래 노드와 동일한 레벨 유지
            int currentLevel = splitNode.getLevel();
            Node leftNode = Node.createInternal(leftMbr, splitNode.getParent(), leftChildren, currentLevel);
            Node rightNode = Node.createInternal(rightMbr, splitNode.getParent(), rightChildren, currentLevel);

            // 자식 노드들의 부모 포인터를 새 노드로 변경
            for (Node child : leftChildren) {
                child.setParent(leftNode);
            }
            for (Node child : rightChildren) {
                child.setParent(rightNode);
            }

            if (splitNode.getParent() == null) {
                // === 루트 노드가 분할되는 경우 ===
                ArrayList<Node> rootChildren = new ArrayList<>();
                rootChildren.add(leftNode);
                rootChildren.add(rightNode);

                Rectangle newRootMbr = RectangleBuilder.fromNodes(rootChildren);

                // 새로운 루트 노드 생성
                Node newRoot = Node.createInternal(newRootMbr, null, rootChildren, 0);
                leftNode.setParent(newRoot);
                rightNode.setParent(newRoot);
                root = newRoot;
                adjustLevelsAfterPromotion(root);
            } else {
                // === 일반 노드 분할 처리 ===
                Node parent = splitNode.getParent();
                List<Node> siblings = parent.getChildren();
                siblings.remove(splitNode);
                siblings.add(leftNode);
                siblings.add(rightNode);
                leftNode.setParent(parent);
                rightNode.setParent(parent);
                
                Rectangle newParentMbr = RectangleBuilder.fromNodes(siblings);
                parent.setMbr(newParentMbr);

                if (siblings.size() > MAX_CHILDREN) {
                    splitNode(parent);
                } else {
                    adjustTreeAfterInsertion(parent);
                }
            }
        }
    }

    /**
     * 적절한 리프 노드를 찾는 함수
     * 루트부터 시작해서 MBR 확장이 가장 적은 경로를 따라 내려감
     */
    private Node chooseLeaf(Node start, Point point) {
        Node current = start;

        // 리프 노드에 도달할 때까지 반복
        while (current != null) {
            // 리프 노드에 도달하면 반환
            if (current.isLeaf()) {
                return current;
            }

            // === 내부 노드인 경우 ===
            // 자식 노드들 중에서 MBR 확장 면적이 가장 작은 노드를 선택
            List<Node> children = current.getChildren();
            Node minChild = null;
            double minEnlargement = Double.MAX_VALUE;

            // 모든 자식 노드를 검사
            for (Node child : children) {
                // 자식의 MBR을 point를 포함하도록 확장했을 때의 새 MBR
                Rectangle mbr = child.getMbr();
                Rectangle enlarged = mbr.expandToInclude(point);

                // 면적 증가량 계산 (확장 후 면적 - 원래 면적)
                double enlargement = enlarged.getArea() - mbr.getArea();

                // 최소 면적 증가량을 가진 자식 선택
                if (enlargement < minEnlargement) {
                    minEnlargement = enlargement;
                    minChild = child;
                }
            }

            // 선택된 자식으로 포인터 이동
            current = minChild;
        }

        // 정상적인 경우 이 코드에 도달하지 않음
        // 도달했다면 트리 구조에 문제가 있음
        System.err.println("Error: chooseLeaf가 null을 반환함");
        return null;
    }

    /**
     * 포인트가 트리에 존재하는지 확인해서 Point를 포함하는 노드를 반환
     * 존재하지 않으면 null 반환
     */
    public Node contains(Point point) {
        if (root == null) {
            return null;
        }

        return containsRecursive(root, point);
    }

    /**
     * 재귀적으로 노드를 탐색하며 포인트 존재 여부 확인
     */
    private Node containsRecursive(Node node, Point point) {
        // 1. 현재 노드의 MBR이 포인트를 포함하지 않으면 null 반환
        if (!node.getMbr().contains(point)) {
            return null;
        }

        // 2. 리프 노드에 도달한 경우
        if (node.isLeaf()) {
            // 리프 노드의 포인트 리스트에서 정확히 일치하는 포인트 찾기
            List<Point> points = node.getPoints();
            for (Point p : points) {
                if (p.ApproxEquals(point)) {
                    return node;
                }
            }
            return null;
        }

        // 3. 내부 노드인 경우 - 자식 노드들을 재귀적으로 탐색
        List<Node> children = node.getChildren();
        for (Node child : children) {
            // MBR이 포인트를 포함하는 자식만 탐색
            if (child.getMbr().contains(point)) {
                Node result = containsRecursive(child, point);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    @Override
    public Iterator<Point> search(Rectangle rectangle) {
        if (root == null) {
            // 트리가 비어있으면 빈 이터레이터 반환
            return Collections.emptyIterator();
        }

        // 루트에서 시작해 재귀적으로 검색
        ArrayList<Point> result = new ArrayList<>();
        ArrayList<Node> prunedNodes = new ArrayList<>();

        if (listener != null) {
            listener.onSearchStarted(this, rectangle);
        }

        searchRecursive(root, rectangle, result, prunedNodes);

        if (listener != null) {
            listener.onSearchCompleted(this, result, prunedNodes);
        }
        return result.iterator();
    }

    /**
     * 재귀적으로 노드를 탐색하며 사각형과 겹치는 포인트 수집
     */
    private void searchRecursive(Node current, Rectangle rectangle, ArrayList<Point> result,
            ArrayList<Node> prunedNodes) {
        // 현재 노드의 MBR이 검색 사각형과 겹치지 않으면 종료

        boolean isPruned = !current.getMbr().intersects(rectangle);

        if (listener != null) {
            listener.onSearchStep(current, isPruned);
        }

        if (isPruned) {
            prunedNodes.add(current);
            return;
        }

        // 리프 노드인 경우, 포인트들을 검사
        if (current.isLeaf()) {
            List<Point> points = current.getPoints();
            for (Point p : points) {
                if (rectangle.contains(p)) {
                    result.add(p);
                }
            }
            return;
        }

        // 내부 노드인 경우, 자식 노드들을 재귀적으로 탐색
        List<Node> children = current.getChildren();
        for (Node child : children) {
            searchRecursive(child, rectangle, result, prunedNodes);
        }
    }

    @Override
    public Iterator<Point> nearest(Point source, int maxCount) {
        // 트리가 비어있거나 maxCount가 0 이하인 경우 빈 이터레이터 반환
        if (root == null || maxCount <= 0) {
            return Collections.emptyIterator();
        }

        // 결과 포인트 (거리순)
        ArrayList<Point> result = new ArrayList<>();
        // 방문한 노드들 기록
        ArrayList<Node> visitedNodes = new ArrayList<>();

        // 우선순위 큐: 거리 오름차순
        // 큐에는 노드 또는 포인트가 들어감
        PriorityQueue<PQEntry> pq = new PriorityQueue<>((a, b) -> Double.compare(a.dist, b.dist));

        if (listener != null) {
            listener.onKNNStarted(this, source, maxCount);
        }

        // 루트 삽입
        if (root.isLeaf()) {
            // 루트가 리프 노드인 경우, 모든 포인트 삽입
            for (Point p : root.getPoints()) {
                pq.add(new PQEntry(null, p, p.distance(source)));
            }
        } else {
            // 루트가 내부 노드인 경우, MBR과 소스 포인트 간의 최소 거리 계산해 삽입
            double d = root.getMbr().minDist(source);
            pq.add(new PQEntry(root, null, d));
        }

        // 우선순위 큐가 빌 때까지 또는 원하는 개수만큼 찾을 때까지 반복
        while (!pq.isEmpty() && result.size() < maxCount) {
            PQEntry entry = pq.poll();

            // 포인트인 경우 결과에 추가
            if (entry.point != null) {
                result.add(entry.point);

                // 리스너 호출
                if (listener != null) {
                    listener.onKNNStep(this, source, result, visitedNodes, entry);
                }
                continue;
            }

            Node node = entry.node;
            
            // 방문한 노드 기록
            visitedNodes.add(node);
            // 리스너 호출
            if (listener != null) {
                listener.onKNNStep(this, source, result, visitedNodes, entry);
            }
            
            // 노드일 경우, 자식 노드 또는 포인트들을 큐에 삽입
            if (node.isLeaf()) {
                for (Point p : node.getPoints()) {
                    pq.add(new PQEntry(null, p, p.distance(source)));
                }
            } else {
                for (Node child : node.getChildren()) {
                    // 자식 노드의 MBR과 소스 포인트 간의 최소 거리 계산해 삽입
                    double d = child.getMbr().minDist(source);
                    pq.add(new PQEntry(child, null, d));
                }
            }
        }

        // 완료 리스너 호출
        if (listener != null) {
            listener.onKNNCompleted(this, source, result);
        }

        return result.iterator();
    }

    @Override
    public boolean isEmpty() {
        // root의 null 여부로 트리 비어있는지 판단
        return root == null;
    }

    @Override
    public void delete(Point point) {
        // point가 트리에 존재하는지 확인
        Node leafNode = contains(point);
        if (leafNode == null) {
            // 트리에 존재하지 않으면 삭제할 필요 없음
            return;
        }

        // 리프 노드에서 포인트 제거
        List<Point> points = leafNode.getPoints();
        // ApproxEquals를 사용하여 일치하는 포인트 제거
        points.removeIf(p -> p.ApproxEquals(point));

        // 언더플로우 발생 시 재구성, 그렇지 않으면 MBR만 업데이트
        if (points.size() < MIN_CHILDREN) {
            // 언더플로우 발생 시 트리 재구성
            condenseTree(leafNode);
        } else {
            // 언더플로우가 아니면 MBR만 업데이트
            adjustTreeAfterInsertion(leafNode);
        }

        // 루트가 비게 되면 null로 설정
        if (root != null &&
                ((root.isLeaf() && root.getPoints().isEmpty()) // 루트가 리프 노드이고 포인트가 비어있거나
                        || (!root.isLeaf() && root.getChildren().isEmpty()))) { // 루트가 내부 노드이고 자식이 비어있는 경우
            root = null;
        }

        // 리스너 호출
        if (listener != null) {
            listener.onRTreeChanged(this);
        }
    }

    /**
     * 노드를 삭제하고 트리를 재구성하는 함수
     * 삭제 후 언더플로우가 발생한 노드부터 루트까지 올라가며 처리
     */
    private void condenseTree(Node node) {
        Node current = node;
        // 재삽입할 포인트들과 노드들 저장
        List<Point> reinsertPoints = new ArrayList<>();
        List<Node> reinsertNodes = new ArrayList<>();

        // 루트에 도달할 때까지 반복
        while (current != root) {
            Node parent = current.getParent();
            List<Node> siblings = parent.getChildren();

            // 현재 노드가 언더플로우 상태인지 확인 (리프면 포인트 수, 내부면 자식 수)
            boolean isUnderflow = current.isLeaf() ? current.getPoints().size() < MIN_CHILDREN
                    : current.getChildren().size() < MIN_CHILDREN;

            if (isUnderflow) {
                // 언더플로우 발생: 부모에서 현재 노드 제거
                siblings.remove(current);

                // 현재 노드의 내용을 재삽입 목록에 추가
                if (current.isLeaf()) {
                    // 리프 노드면 포인트들을 재삽입 목록에 추가
                    reinsertPoints.addAll(current.getPoints());
                } else {
                    // 내부 노드면 자식 노드들을 재삽입 목록에 추가
                    reinsertNodes.addAll(current.getChildren());
                }
            } else {
                // 언더플로우가 아니면 MBR만 업데이트
                if (current.isLeaf()) {
                    current.setMbr(RectangleBuilder.fromPoints(current.getPoints()));
                } else {
                    current.setMbr(RectangleBuilder.fromNodes(current.getChildren()));
                }
            }
            // 다음 레벨로 올라감
            current = parent;
        }

        // 루트 노드 처리 및 레벨 조정
        if (root != null) {
            if (!root.isLeaf()) {
                // 루트가 내부 노드인 경우
                if (root.getChildren().size() == 0) {
                    // 자식이 없으면 트리를 비움
                    root = null;
                } else if (root.getChildren().size() == 1) {
                    // 자식이 하나만 있으면 그 자식을 새 루트로 승격
                    root = root.getChildren().get(0);
                    root.setParent(null);
                    // 승격된 노드를 루트로 만들면서 전체 트리의 레벨 재조정
                    adjustLevelsAfterPromotion(root);
                } else {
                    // 자식이 여러 개면 MBR만 업데이트
                    root.setMbr(RectangleBuilder.fromNodes(root.getChildren()));
                }
            } else {
                // 루트가 리프 노드인 경우
                if (root.getPoints().isEmpty()) {
                    // 포인트가 없으면 트리를 비움
                    root = null;
                } else {
                    // 포인트가 있으면 MBR만 업데이트
                    root.setMbr(RectangleBuilder.fromPoints(root.getPoints()));
                }
            }
        }

        // 재삽입 목록에 있는 모든 포인트들을 다시 트리에 추가
        // add 메서드는 내부적으로 MBR 업데이트를 처리함
        for (Point p : reinsertPoints) {
            add(p);
        }

        // 재삽입 목록에 있는 모든 노드들을 다시 트리에 추가
        for (Node n : reinsertNodes) {
            // add를 재사용하기 위해 노드의 모든 포인트들을 가져와서 삽입
            Iterator<Point> pointsIterator = getAllPoints(n);
            while (pointsIterator.hasNext()) {
                add(pointsIterator.next());
            }
        }
    }

    /**
     * 노드에 포함된 모든 포인트를 이터레이터로 반환
     */
    public Iterator<Point> getAllPoints(Node node) {
        ArrayList<Point> result = new ArrayList<>();
        getAllPointsRecursive(node, result);
        return result.iterator();
    }

    private void getAllPointsRecursive(Node current, ArrayList<Point> result) {
        if (current.isLeaf()) {
            result.addAll(current.getPoints());
            return;
        }
        for (Node child : current.getChildren()) {
            getAllPointsRecursive(child, result);
        }
    }

    /**
     * 노드 승격 후 전체 트리의 레벨을 재조정
     * 루트에서부터 시작해 모든 자식의 레벨을 부모보다 1 크게 설정
     */
    private void adjustLevelsAfterPromotion(Node node) {
        if (node == null)
            return;

        // 루트는 항상 레벨 0이어야 함
        if (node.getParent() == null) {
            updateLevelsRecursive(node, 0);
        }
    }

    /**
     * 재귀적으로 모든 노드의 레벨을 업데이트
     * 부모-자식 관계에서 자식은 항상 부모보다 1 큰 레벨을 가짐
     */
    private void updateLevelsRecursive(Node node, int level) {
        // 현재 노드의 레벨 설정
        node.setLevel(level);

        // 내부 노드인 경우 모든 자식 노드의 레벨을 재귀적으로 업데이트
        if (!node.isLeaf()) {
            for (Node child : node.getChildren()) {
                updateLevelsRecursive(child, level + 1);
            }
        }
    }
}
