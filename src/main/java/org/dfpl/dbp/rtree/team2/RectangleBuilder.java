package org.dfpl.dbp.rtree;

import java.util.List;

public class RectangleBuilder {
    /**
     * Point 리스트를 모두 포함하는 Rectangle 생성
     */
    public static Rectangle fromPoints(List<Point> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Point 리스트가 비어있습니다");
        }

        // 첫번째 Point로 초기값 설정
        Point first = points.get(0);
        double minX = first.getX();
        double minY = first.getY();
        double maxX = first.getX();
        double maxY = first.getY();

        // 모든 Point를 순회하며 최소/최대 좌표 갱신
        for (int i = 1; i < points.size(); i++) {
            Point p = points.get(i);
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }

        return new Rectangle(new Point(minX, maxY), new Point(maxX, minY));
    }

    /**
     * 여러 Rectangle들을 모두 포함하는 Rectangle 생성
     */
    public static Rectangle fromRectangles(List<Rectangle> rectangles) {
        if (rectangles == null || rectangles.isEmpty()) {
            throw new IllegalArgumentException("Rectangle 리스트가 비어있습니다");
        }

        // 첫 번째 Rectangle로 초기화
        Rectangle first = rectangles.get(0);
        double minX = first.getLeftTop().getX();
        double maxY = first.getLeftTop().getY();
        double maxX = first.getRightBottom().getX();
        double minY = first.getRightBottom().getY();

        // 모든 Rectangle을 순회하며 최소/최대 좌표 갱신
        for (int i = 1; i < rectangles.size(); i++) {
            Rectangle rect = rectangles.get(i);

            // 왼쪽 위 (leftTop) 좌표 처리
            minX = Math.min(minX, rect.getLeftTop().getX());
            maxY = Math.max(maxY, rect.getLeftTop().getY());

            // 오른쪽 아래 (rightBottom) 좌표 처리
            maxX = Math.max(maxX, rect.getRightBottom().getX());
            minY = Math.min(minY, rect.getRightBottom().getY());
        }

        // 최종 Rectangle 1개만 생성
        return new Rectangle(new Point(minX, maxY), new Point(maxX, minY));
    }

    public static Rectangle fromNodes(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Node 리스트가 비어있습니다");
        }

        // 첫 번째 Node의 MBR로 초기화
        Rectangle first = nodes.get(0).getMbr();
        double minX = first.getLeftTop().getX();
        double maxY = first.getLeftTop().getY();
        double maxX = first.getRightBottom().getX();
        double minY = first.getRightBottom().getY();

        // 모든 Node의 MBR을 순회하며 최소/최대 좌표 갱신
        for (int i = 1; i < nodes.size(); i++) {
            Rectangle rect = nodes.get(i).getMbr();

            // 왼쪽 위 (leftTop) 좌표 처리
            minX = Math.min(minX, rect.getLeftTop().getX());
            maxY = Math.max(maxY, rect.getLeftTop().getY());

            // 오른쪽 아래 (rightBottom) 좌표 처리
            maxX = Math.max(maxX, rect.getRightBottom().getX());
            minY = Math.min(minY, rect.getRightBottom().getY());
        }

        // 최종 Rectangle 1개만 생성
        return new Rectangle(new Point(minX, maxY), new Point(maxX, minY));
    }
}
