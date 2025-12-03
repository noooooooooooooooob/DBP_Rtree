package org.dfpl.dbp.rtree.team2.test;

import org.dfpl.dbp.rtree.team2.Point;
import org.dfpl.dbp.rtree.team2.RTreeImpl;
import org.dfpl.dbp.rtree.team2.Rectangle;

import java.util.*;

public class TestScore {
    public static long getRunTime(Runnable method) {
        long start = System.currentTimeMillis();
        method.run();
        return System.currentTimeMillis() - start;
    }

    public static void main(String[] args) {
        // 테스트용 데이터
        int DATA_SIZE = 100000; // 데이터 개수
        int BOUND_X = 10000;    // x 좌표 범위
        int BOUND_Y = 10000;    // y 좌표 범위
        int REMOVE_CNT = 100000;

        System.out.println("데이터 " + DATA_SIZE + "개 생성 중...");
        List<Point> pointList = new ArrayList<>(DATA_SIZE);
        Random random = new Random(12345); // 시드값 고정 (매번 같은 랜덤 결과가 나오도록)

        for (int i = 0; i < DATA_SIZE; i++) {
            double x = random.nextInt(BOUND_X);
            double y = random.nextInt(BOUND_Y);
            pointList.add(new Point(x, y));
        }
        System.out.println("데이터 생성 완료.\n");

        // RTree, ArrayList 생성
        RTreeImpl rTree = new RTreeImpl(null);
        ArrayList<Point> points = new ArrayList<>();

        // 1. ADD 테스트
        long listAddTime = getRunTime(() -> {
            for (Point point : pointList) {
                points.add(point);
            }
        });
        long rTreeAddTime = getRunTime(() -> {
            for (Point point : pointList) {
                rTree.add(point);
            }
        });

        System.out.println("Add - ArrayList: " + listAddTime + "ms, RTree: " + rTreeAddTime + "ms");

        // 검색 범위: (20, 20) ~ (60, 60) 사각형 영역이라 가정
        Rectangle searchRect = new Rectangle(new Point(20, 20),  new Point(60, 60));

        long listSearchTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            // ArrayList는 전체를 순회하며 범위 체크
            for (Point p : points) {
                if (p.getX() >= 20 && p.getX() <= 60 && p.getY() >= 20 && p.getY() <= 60) {
                    result.add(p);
                }
            }
            System.out.println("List Search Result: " + result.size());
        });

        long rTreeSearchTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            Iterator<Point> it = rTree.search(searchRect);
            while (it.hasNext()) {
                result.add(it.next());
            }
            System.out.println("RTree Search Result: " + result.size());
        });

        System.out.println("Search - ArrayList: " + listSearchTime + "ms, RTree: " + rTreeSearchTime + "ms");

        Point targetPoint = new Point(50, 50);
        int k = 5; // 가장 가까운 5개 찾기

        long listNearestTime = getRunTime(() -> {
            // ArrayList는 거리 계산 후 정렬 필요 (O(N log N) or O(N))
            List<Point> result = points.stream()
                    .sorted(Comparator.comparingDouble(p ->
                            Math.pow(p.getX() - targetPoint.getX(), 2) + Math.pow(p.getY() - targetPoint.getY(), 2)
                    ))
                    .limit(k)
                    .toList();
            System.out.println("List Nearest Point: " + result.size());
        });

        long rTreeNearestTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            Iterator<Point> it = rTree.nearest(targetPoint, k);
            while (it.hasNext()) {
                result.add(it.next());
            }
            System.out.println("RTree Nearest Point: " + result.size());
        });

        System.out.println("Nearest - ArrayList: " + listNearestTime + "ms, RTree: " + rTreeNearestTime + "ms");

        // 리스트에 있는 포인트 중 일부를 삭제
        List<Point> pointsToDelete = pointList.subList(0, REMOVE_CNT);

        long listDeleteTime = getRunTime(() -> {
            for (Point p : pointsToDelete) {
                points.remove(p); // 선형 탐색 후 삭제 O(N)
            }
        });

        long rTreeDeleteTime = getRunTime(() -> {
            for (Point p : pointsToDelete) {
                rTree.delete(p); // 트리 탐색 후 삭제 O(log N)
            }
        });

        System.out.println("Delete - ArrayList: " + listDeleteTime + "ms, RTree: " + rTreeDeleteTime + "ms");

        // 최종 검증
        System.out.println("RTree isEmpty? " + rTree.isEmpty());
    }
}