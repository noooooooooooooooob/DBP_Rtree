package org.dfpl.dbp.rtree;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * R-Tree 시각화를 위한 메인 프레임
 * RTreeListener 인터페이스를 구현하여 R-Tree의 변경사항을 감지하고 GUI를 업데이트
 */
public class MainFrame extends JFrame implements RTreeListener {

    private RTreePanel treePanel;
    
    // 상태 정보를 표시할 라벨들
    private JLabel statusLabel;
    private JLabel statsLabel;

    // === 시각화 상태 변수들 ===
    
    /** 현재 시각화 중인 R-Tree */
    private RTree currentTree;
    
    /** 검색 영역 (녹색 사각형으로 표시) */
    private Rectangle searchArea;
    
    /** 검색 결과로 찾은 포인트들 (파란색으로 강조) */
    private List<Point> foundPoints = new ArrayList<>();
    
    /** 가지치기된 노드들 (빨간색으로 표시) */
    private List<Node> prunedNodes = new ArrayList<>();
    
    /** 방문한 노드들 (KNN 검색에서 노란색으로 표시) */
    private List<Node> visitedNodes = new ArrayList<>();
    
    /** 스캔 중인 포인트들 (현재 검사 중인 리프 노드의 포인트) */
    private List<Point> scanningPoints = new ArrayList<>();
    
    /** 현재 검사 중인 포인트 (리프 노드 내부에서 하나씩 검사) */
    private Point currentScanningPoint;
    
    /** KNN 검색의 기준 포인트 (빨간색으로 표시) */
    private Point knnSource;
    
    /** KNN 검색 결과 포인트들 (마젠타색으로 표시되며 기준점과 선으로 연결) */
    private List<Point> knnResults = new ArrayList<>();
    
    /** KNN 검색 중 우선순위 큐에서 현재 처리 중인 엔트리 */
    private PQEntry currentEntry;
    
    /** 일반 검색 중 현재 처리 중인 노드 (오렌지색으로 강조) */
    private Node currentSearchNode;

    /**
     * MainFrame 생성자
     * GUI 초기 설정 및 RTreePanel 추가
     */
    public MainFrame() {
        setTitle("R-Tree Visualization");
        setSize(1000, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // R-Tree를 그리는 패널 생성 및 추가
        treePanel = new RTreePanel(this);
        add(treePanel, BorderLayout.CENTER);
        
        // 하단 상태 패널 추가
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        infoPanel.setBackground(new Color(240, 240, 240));
        
        statusLabel = new JLabel("준비 완료");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setForeground(new Color(50, 50, 50));
        
        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font("Monospaced", Font.PLAIN, 14));
        
        infoPanel.add(statusLabel, BorderLayout.WEST);
        infoPanel.add(statsLabel, BorderLayout.EAST);
        
        add(infoPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    /**
     * R-Tree가 변경될 때 호출 (포인트 추가/삭제)
     * 모든 시각화 상태를 초기화하고 트리만 표시
     * 
     * @param rTree 변경된 R-Tree
     */
    @Override
    public void onRTreeChanged(RTree rTree) {
        // 현재 트리 업데이트
        this.currentTree = rTree;
        
        // 검색 관련 상태 초기화
        this.searchArea = null;
        this.foundPoints.clear();
        this.prunedNodes.clear();
        this.visitedNodes.clear();
        this.scanningPoints.clear();
        this.currentScanningPoint = null;
        
        // KNN 관련 상태 초기화
        this.knnSource = null;
        this.knnResults.clear();
        this.currentEntry = null;
        
        // 일반 검색 상태 초기화
        this.currentSearchNode = null;

        updateStatus("트리 업데이트됨", "노드 수: " + countNodes(((RTreeImpl)rTree).getRoot()));
        
        // 화면 갱신
        treePanel.repaint();

        // 애니메이션 효과를 위한 짧은 대기
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private int countNodes(Node node) {
        if (node == null) return 0;
        int count = 1;
        if (!node.isLeaf()) {
            for (Node child : node.getChildren()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    /**
     * 검색이 시작될 때 호출
     * 검색 영역을 설정하고 이전 검색 결과를 초기화
     * 
     * @param rTree 검색을 수행하는 R-Tree
     * @param searchArea 검색 영역 사각형
     */
    @Override
    public void onSearchStarted(RTree rTree, Rectangle searchArea) {
        this.currentTree = rTree;
        this.searchArea = searchArea;
        
        // 검색 관련 상태 초기화
        this.foundPoints.clear();
        this.prunedNodes.clear();
        this.visitedNodes.clear();
        this.scanningPoints.clear();
        this.currentScanningPoint = null;
        this.currentSearchNode = null;
        
        updateStatus("검색 시작...", "영역: " + searchArea);
        
        // 화면 갱신
        treePanel.repaint();
    }

    /**
     * 검색의 각 단계마다 호출
     * 현재 검사 중인 노드를 표시하고, 가지치기 여부에 따라 상태 업데이트
     * 
     * @param visitedNode 현재 방문한 노드
     * @param isPruned 가지치기 여부 (검색 영역과 겹치지 않음)
     */
    @Override
    public void onSearchStep(Node visitedNode, boolean isPruned) {
        // 현재 처리 중인 노드 설정 (오렌지색으로 강조됨)
        this.currentSearchNode = visitedNode;
        
        if (isPruned) {
            // 가지치기된 노드는 prunedNodes 리스트에 추가
            // 빨간색으로 표시되어 검색에서 제외됨을 표현
            if (!prunedNodes.contains(visitedNode)) {
                prunedNodes.add(visitedNode);
            }
        } else {
            // 가지치기되지 않은 노드는 visitedNodes에 추가
            if (!visitedNodes.contains(visitedNode)) {
                visitedNodes.add(visitedNode);
            }

            // 리프 노드인 경우 포인트들을 하나씩 검사
            if (visitedNode.isLeaf()) {
                scanningPoints.clear();
                
                // 리프 노드의 각 포인트를 순회하며 검사
                for (Point point : visitedNode.getPoints()) {
                    // 현재 검사 중인 포인트 설정
                    currentScanningPoint = point;
                    
                    updateStatus("리프 포인트 스캔 중...", 
                        String.format("방문: %d | 가지치기: %d | 찾음: %d", 
                        visitedNodes.size(), prunedNodes.size(), foundPoints.size()));
                    
                    treePanel.repaint();

                    // 포인트 검사 애니메이션을 위한 대기
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // 포인트가 검색 영역 내부에 있는지 확인
                    if (searchArea.contains(point)) {
                        if (!foundPoints.contains(point)) {
                            foundPoints.add(point);
                        }
                    }
                    
                    // 검사 완료된 포인트 추가
                    scanningPoints.add(point);
                }
                
                // 리프 노드의 모든 포인트 검사 완료
                currentScanningPoint = null;
            }
        }

        updateStatus("트리 검색 중...", 
            String.format("방문: %d | 가지치기: %d | 찾음: %d", 
            visitedNodes.size(), prunedNodes.size(), foundPoints.size()));

        // 화면 갱신
        treePanel.repaint();

        // 노드 방문 애니메이션을 위한 대기
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 검색이 완료될 때 호출
     * 최종 검색 결과를 업데이트하고 현재 검사 상태를 초기화
     * 
     * @param rTree 검색을 수행한 R-Tree
     * @param foundPoints 검색된 포인트들
     * @param prunedNodes 가지치기된 노드들
     */
    @Override
    public void onSearchCompleted(RTree rTree, List<Point> foundPoints, List<Node> prunedNodes) {
        // 최종 결과를 복사하여 저장
        this.foundPoints = new ArrayList<>(foundPoints);
        this.prunedNodes = new ArrayList<>(prunedNodes);
        
        // 검사 중 상태 초기화
        this.currentScanningPoint = null;
        this.currentSearchNode = null;
        
        updateStatus("검색 완료", 
            String.format("총 찾은 개수: %d 개", foundPoints.size()));
        
        // 화면 갱신
        treePanel.repaint();

        // 결과 확인을 위한 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * KNN 검색이 시작될 때 호출
     * KNN 검색 상태를 초기화하고 기준 포인트 설정
     * 
     * @param rTree 검색을 수행하는 R-Tree
     * @param source 기준 포인트
     * @param k 찾을 포인트 개수
     */
    @Override
    public void onKNNStarted(RTree rTree, Point source, int k) {
        this.currentTree = rTree;
        this.knnSource = source;
        
        // KNN 관련 상태 초기화
        this.knnResults.clear();
        this.visitedNodes.clear();
        this.currentEntry = null;
        
        // 일반 검색 상태 초기화
        this.searchArea = null;
        this.foundPoints.clear();
        this.prunedNodes.clear();
        this.scanningPoints.clear();
        this.currentScanningPoint = null;
        this.currentSearchNode = null;
        
        updateStatus("KNN 검색 시작", k + "개의 최근접 이웃 찾기");
        
        // 화면 갱신
        treePanel.repaint();
    }

    /**
     * KNN 검색의 각 단계마다 호출
     * 우선순위 큐에서 처리된 엔트리와 현재까지의 결과를 표시
     * 
     * @param rTree 검색 중인 R-Tree
     * @param source 기준 포인트
     * @param currentResults 현재까지 찾은 k개의 가까운 포인트들
     * @param visitedNodes 방문한 노드들
     * @param currentNode 현재 처리 중인 우선순위 큐 엔트리
     */
    @Override
    public void onKNNStep(RTree rTree, Point source, List<Point> currentResults, List<Node> visitedNodes,
            PQEntry currentNode) {
        // 현재 결과를 복사하여 저장
        this.knnResults = new ArrayList<>(currentResults);
        this.visitedNodes = new ArrayList<>(visitedNodes);
        this.currentEntry = currentNode;
        
        updateStatus("KNN 검색 중...", 
            String.format("찾음: %d | 방문한 노드: %d", 
            currentResults.size(), visitedNodes.size()));
        
        // 화면 갱신
        treePanel.repaint();

        // 단계별 처리 애니메이션을 위한 대기
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * KNN 검색이 완료될 때 호출
     * 최종 k개의 가까운 포인트들을 표시
     * 
     * @param rTree 검색을 수행한 R-Tree
     * @param source 기준 포인트
     * @param results 최종 k개의 가까운 포인트들
     */
    @Override
    public void onKNNCompleted(RTree rTree, Point source, List<Point> results) {
        // 최종 결과를 복사하여 저장
        this.knnResults = new ArrayList<>(results);
        
        // 현재 처리 중인 엔트리 초기화
        this.currentEntry = null;
        
        updateStatus("KNN 완료", results.size() + "개의 이웃을 찾았습니다");
        
        // 화면 갱신
        treePanel.repaint();

        // 결과 확인을 위한 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void updateStatus(String status, String stats) {
        if (statusLabel != null) statusLabel.setText(status);
        if (statsLabel != null) statsLabel.setText(stats);
    }

    // === Getter 메서드들 ===
    // RTreePanel에서 시각화 상태를 가져가기 위한 메서드들
    
    public RTree getCurrentTree() {
        return currentTree;
    }

    public Rectangle getSearchArea() {
        return searchArea;
    }

    public List<Point> getFoundPoints() {
        return foundPoints;
    }

    public List<Node> getPrunedNodes() {
        return prunedNodes;
    }

    public List<Node> getVisitedNodes() {
        return visitedNodes;
    }

    public List<Point> getScanningPoints() {
        return scanningPoints;
    }

    public Point getCurrentScanningPoint() {
        return currentScanningPoint;
    }

    public Point getKnnSource() {
        return knnSource;
    }

    public List<Point> getKnnResults() {
        return knnResults;
    }

    public PQEntry getCurrentEntry() {
        return currentEntry;
    }

    public Node getCurrentSearchNode() {
        return currentSearchNode;
    }
}
