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
        int DATA_SIZE = 1000000; // 데이터 개수
        int BOUND_X = 10000;    // x 좌표 범위
        int BOUND_Y = 10000;    // y 좌표 범위
        int REMOVE_CNT = 1000;

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

        // 검색하기
        double minX = BOUND_X * 0.4;
        double minY = BOUND_Y * 0.4;
        double maxX = BOUND_X * 0.6;
        double maxY = BOUND_Y * 0.6;

        // 1000000개 데이터 기준
        // ArrayList는 탐색범위에 상관없이 항상 일정한 시간이 걸리나
        // RTree는 탐색범위가 커질수록 시간이 오래걸린다.

        Rectangle searchRect = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));

        long listSearchTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            for (Point p : points) {
                if (p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY) {
                    result.add(p);
                }
            }
            // System.out.println("   -> List Found: " + result.size());
        });

        long rTreeSearchTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            Iterator<Point> it = rTree.search(searchRect);
            while (it.hasNext()) {
                result.add(it.next());
            }
            // System.out.println("   -> RTree Found: " + result.size());
        });

        System.out.println("Search - ArrayList: " + listSearchTime + "ms, RTree: " + rTreeSearchTime + "ms");

        Point targetPoint = new Point(BOUND_X / 2.0, BOUND_Y / 2.0); // 정중앙
        int k = 10;

        long listNearestTime = getRunTime(() -> {
            List<Point> result = points.stream()
                    .sorted(Comparator.comparingDouble(p ->
                            Math.pow(p.getX() - targetPoint.getX(), 2) + Math.pow(p.getY() - targetPoint.getY(), 2)
                    ))
                    .limit(k)
                    .toList();
        });

        long rTreeNearestTime = getRunTime(() -> {
            List<Point> result = new ArrayList<>();
            Iterator<Point> it = rTree.nearest(targetPoint, k);
            while (it.hasNext()) {
                result.add(it.next());
            }
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