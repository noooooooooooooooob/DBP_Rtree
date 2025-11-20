package org.dfpl.dbp.rtree;

import java.util.List;

/**
 * R-Tree의 변경사항을 감지하고 시각화를 위한 리스너 인터페이스
 */
public interface RTreeListener {
    
    /**
     * 트리 구조가 변경될 때 호출 (포인트 추가/삭제 등)
     * 
     * @param rTree 현재 R-Tree
     */
    void onRTreeChanged(RTree rTree);
    
    /**
     * 검색 작업이 수행될 때 호출
     * 
     * @param rTree 현재 R-Tree
     * @param searchArea 검색 영역
     * @param foundPoints 검색된 포인트들
     * @param prunedNodes 가지치기된 노드들 (검색 범위와 겹치지 않음)
     */
    void onSearchStarted(RTree rTree, Rectangle searchArea);

    /**
     * 검색의 각 단계가 진행될 때 호출
     * @param rTree
     * @param searchArea
     */
    void onSearchStep(Node visitedNodes, boolean isPruned);

    /**
     * 검색이 완료될 때 호출
     * @param rTree
     * @param foundPoints
     * @param prunedNodes
     */
    void onSearchCompleted(RTree rTree, List<Point> foundPoints, List<Node> prunedNodes);
    
    /**
     * KNN 검색이 시작될 때 호출
     * @param rTree
     * @param source
     * @param k
     */
    void onKNNStarted(RTree rTree, Point source, int k);

    /**
     * KNN 검색의 각 단계가 진행될 때 호출
     * 
     * @param rTree 현재 R-Tree
     * @param source 기준 포인트
     * @param currentResults 현재까지 찾은 포인트들 (거리순)
     * @param visitedNodes 방문한 노드들
     * @param currentNode 현재 검사 중인 노드 (강조 표시용)
     */
    void onKNNStep(RTree rTree, Point source, List<Point> currentResults, List<Node> visitedNodes, PQEntry currentNode);
    
    /**
     * KNN 검색이 완료될 때 호출
     * 
     * @param rTree 현재 R-Tree
     * @param source 기준 포인트
     * @param results 최종 k개의 포인트들
     */
    void onKNNCompleted(RTree rTree, Point source, List<Point> results);
}
