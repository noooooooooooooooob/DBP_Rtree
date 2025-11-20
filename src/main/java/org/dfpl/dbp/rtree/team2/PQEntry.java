package org.dfpl.dbp.rtree.team2;

// 우선순위 큐를 위한 정보 저장 클래스
class PQEntry {
    final Node node; // node 또는 point 중 하나만 사용
    final Point point;
    final double dist;

    PQEntry(Node node, Point point, double dist) {
        this.node = node;
        this.point = point;
        this.dist = dist;
    }
}