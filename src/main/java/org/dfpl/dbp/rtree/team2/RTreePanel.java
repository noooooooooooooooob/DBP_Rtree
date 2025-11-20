package org.dfpl.dbp.rtree;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

import javax.swing.JPanel;

/**
 * R-Tree를 시각화하는 패널
 * MainFrame의 상태를 읽어와 트리 구조, 검색 과정, KNN 검색 등을 그래픽으로 표현
 */
public class RTreePanel extends JPanel {
    
    /** 화면 가장자리 여백 (픽셀) */
    private static final int PADDING = 50;
    
    /** 좌표계 스케일 (트리 좌표를 화면 좌표로 변환할 때 사용) */
    private static final double SCALE = 3.5;
    
    /** 시각화 상태를 가져올 MainFrame 참조 */
    private MainFrame mainFrame;
    
    /**
     * RTreePanel 생성자
     * 
     * @param mainFrame 시각화 상태를 제공하는 MainFrame
     */
    public RTreePanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setBackground(Color.WHITE);
    }
    
    /**
     * 패널을 그리는 메인 메서드
     * Swing에 의해 자동으로 호출되며, repaint() 호출 시에도 실행됨
     * 
     * 그리기 순서:
     * 0. 배경 그리드 및 좌표축
     * 1. 계층적 Bounding Box (레벨별 다른 색상)
     * 2. 가지치기된 노드 (빨간색 채우기)
     * 3. 현재 검색 중인 노드 (오렌지색 강조)
     * 4. 검색 영역 (녹색 테두리)
     * 5. 모든 포인트 (검은색 작은 점)
     * 6. 검색된 포인트 (파란색 큰 점)
     * 7. KNN 검색 시각화 (기준점, 결과, 연결선 등)
     * 8. 범례 (Legend)
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        
        // 안티앨리어싱 활성화 (부드러운 선 그리기)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // === 0. 배경 그리드 및 좌표축 그리기 ===
        drawGridAndAxes(g2);
        
        // 현재 트리 가져오기
        RTree currentTree = mainFrame.getCurrentTree();
        if (currentTree == null || currentTree.isEmpty()) {
            // 트리가 없거나 비어있으면 아무것도 그리지 않음
            return;
        }
        
        // 루트 노드부터 시작
        Node root = ((RTreeImpl) currentTree).getRoot();
        
        // === 1. 계층적 Bounding Box 그리기 ===
        // 모든 노드의 MBR을 레벨별로 다른 색상으로 표시
        drawNodeHierarchy(g2, root);
        
        // === 2. 가지치기된 노드 강조 ===
        // 검색 영역과 겹치지 않아 탐색에서 제외된 노드들을 빨간색으로 채움
        if (!mainFrame.getPrunedNodes().isEmpty()) {
            g2.setColor(new Color(255, 0, 0, 100)); // 반투명 빨간색
            for (Node node : mainFrame.getPrunedNodes()) {
                fillRectangle(g2, node.getMbr());
            }
        }
        
        // === 3. 현재 검색 중인 노드 강조 ===
        // 일반 검색에서 현재 처리 중인 노드를 오렌지색으로 표시
        if (mainFrame.getCurrentSearchNode() != null) {
            g2.setColor(new Color(255, 165, 0, 150)); // 반투명 오렌지
            fillRectangle(g2, mainFrame.getCurrentSearchNode().getMbr());
            g2.setColor(new Color(255, 140, 0)); // 진한 오렌지 테두리
            drawRectangle(g2, mainFrame.getCurrentSearchNode().getMbr(), 3);
        }
        
        // === 4. 검색 영역 표시 ===
        // 사용자가 지정한 검색 사각형을 녹색 테두리로 표시
        if (mainFrame.getSearchArea() != null) {
            g2.setColor(new Color(0, 255, 0, 80)); // 반투명 녹색
            drawRectangle(g2, mainFrame.getSearchArea(), 3);
        }
        
        // === 5. 모든 포인트 그리기 ===
        // 트리에 저장된 모든 포인트를 검은색 작은 점으로 표시
        drawAllPoints(g2, root);
        
        // === 6. 검색된 포인트 강조 ===
        // 검색 결과로 찾은 포인트들을 파란색 큰 점으로 표시
        if (!mainFrame.getFoundPoints().isEmpty()) {
            g2.setColor(Color.BLUE);
            for (Point p : mainFrame.getFoundPoints()) {
                drawPoint(g2, p, 8, true);
            }
        }
        
        // === 7. KNN 검색 시각화 ===
        if (mainFrame.getKnnSource() != null) {
            // 7-1. 기준 포인트를 빨간색으로 표시
            g2.setColor(Color.RED);
            drawPoint(g2, mainFrame.getKnnSource(), 10, true);
            
            // 7-2. 방문한 노드들을 노란색으로 채움
            g2.setColor(new Color(255, 255, 0, 100)); // 반투명 노란색
            for (Node node : mainFrame.getVisitedNodes()) {
                fillRectangle(g2, node.getMbr());
            }
            
            // 7-3. 현재 우선순위 큐에서 처리 중인 노드를 오렌지색으로 강조
            if (mainFrame.getCurrentEntry() != null) {
                if (mainFrame.getCurrentEntry().node != null) {
                    g2.setColor(new Color(255, 165, 0, 150)); // 반투명 오렌지
                    fillRectangle(g2, mainFrame.getCurrentEntry().node.getMbr());
                }
            }
            
            // 7-4. KNN 검색 결과 포인트들을 마젠타색으로 표시하고 기준점과 선으로 연결
            g2.setColor(Color.MAGENTA);
            for (int i = 0; i < mainFrame.getKnnResults().size(); i++) {
                Point p = mainFrame.getKnnResults().get(i);
                // 결과 포인트 그리기
                drawPoint(g2, p, 8, true);
                // 기준점과 결과 포인트를 선으로 연결
                g2.drawLine(
                    toScreenX(mainFrame.getKnnSource().getX()), 
                    toScreenY(mainFrame.getKnnSource().getY()),
                    toScreenX(p.getX()), 
                    toScreenY(p.getY())
                );
            }
        }
        
        // === 8. 범례 (Legend) 그리기 ===
        drawLegend(g2);
    }
    
    private void drawGridAndAxes(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        
        // 그리드 (연한 회색)
        g2.setColor(new Color(240, 240, 240));
        g2.setStroke(new BasicStroke(1));
        
        // 50 단위로 그리드 그리기
        for (int i = 0; i <= 200; i += 50) {
            int x = toScreenX(i);
            int y = toScreenY(i);
            
            // 세로선
            g2.drawLine(x, 0, x, h);
            // 가로선
            g2.drawLine(0, y, w, y);
            
            // 좌표 텍스트
            g2.setColor(Color.GRAY);
            g2.drawString(String.valueOf(i), x + 2, h - PADDING + 15); // X축 숫자
            g2.drawString(String.valueOf(i), PADDING - 25, y - 2);     // Y축 숫자
            g2.setColor(new Color(240, 240, 240)); // 다시 그리드 색으로
        }
        
        // 축 (검은색)
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        
        // X축
        g2.drawLine(PADDING, h - PADDING, w - PADDING, h - PADDING);
        // Y축
        g2.drawLine(PADDING, PADDING, PADDING, h - PADDING);
    }
    
    private void drawLegend(Graphics2D g2) {
        int boxW = 160;
        int boxH = 150;
        int x = getWidth() - boxW - 10;
        int y = 10;
        
        // 배경 박스
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRect(x, y, boxW, boxH);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(x, y, boxW, boxH);
        
        int startX = x + 10;
        int startY = y + 20;
        int gap = 20;
        
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("범례", startX, startY);
        
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        // 항목들
        drawLegendItem(g2, startX, startY + gap * 1, new Color(0, 255, 0, 80), "검색 영역", true);
        drawLegendItem(g2, startX, startY + gap * 2, new Color(255, 165, 0, 150), "현재 노드", true);
        drawLegendItem(g2, startX, startY + gap * 3, new Color(255, 0, 0, 100), "가지치기된 노드", true);
        drawLegendItem(g2, startX, startY + gap * 4, Color.BLUE, "찾은 포인트", false);
        drawLegendItem(g2, startX, startY + gap * 5, Color.RED, "KNN 기준점", false);
        drawLegendItem(g2, startX, startY + gap * 6, Color.MAGENTA, "KNN 결과", false);
    }
    
    private void drawLegendItem(Graphics2D g2, int x, int y, Color c, String text, boolean isRect) {
        g2.setColor(c);
        if (isRect) {
            g2.fillRect(x, y - 8, 12, 12);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y - 8, 12, 12);
        } else {
            g2.fillOval(x, y - 8, 10, 10);
        }
        g2.setColor(Color.BLACK);
        g2.drawString(text, x + 20, y + 2);
    }
    
    /**
     * 노드 계층 구조를 재귀적으로 그리는 메서드
     * 각 레벨마다 다른 색상의 사각형으로 MBR을 표시
     * 
     * 색상 규칙:
     * - Level 0 (루트): 파란색
     * - Level 1: 하늘색
     * - Level 2: 보라색
     * - Level 3+: 분홍색
     * 
     * @param g2 그래픽 컨텍스트
     * @param node 그릴 노드
     */
    private void drawNodeHierarchy(Graphics2D g2, Node node) {
        if (node == null) return;
        
        // 레벨별 색상 정의 (반투명)
        Color[] levelColors = {
            new Color(0, 0, 255, 100),      // Level 0: 파란색
            new Color(0, 128, 255, 80),     // Level 1: 하늘색
            new Color(128, 0, 255, 60),     // Level 2: 보라색
            new Color(255, 0, 128, 40)      // Level 3+: 분홍색
        };
        
        // 현재 노드의 레벨에 따른 색상 선택
        int level = node.getLevel();
        Color color = levelColors[Math.min(level, levelColors.length - 1)];
        
        // 노드의 MBR을 해당 색상으로 그리기
        g2.setColor(color);
        drawRectangle(g2, node.getMbr(), 2);
        
        // 노드 레벨 표시
        g2.setColor(Color.BLACK);
        int lx = toScreenX(node.getMbr().getLeftTop().getX());
        int ly = toScreenY(node.getMbr().getLeftTop().getY());
        // 레벨 라벨이 겹치지 않도록 레벨에 따라 Y 위치 조정
        g2.drawString("L" + level, lx + 2, ly + 12 + (level * 14));
        
        // 내부 노드인 경우 자식 노드들도 재귀적으로 그리기
        if (!node.isLeaf()) {
            for (Node child : node.getChildren()) {
                drawNodeHierarchy(g2, child);
            }
        }
    }
    
    /**
     * 트리의 모든 포인트를 재귀적으로 그리는 메서드
     * 리프 노드에 도달할 때까지 탐색하여 모든 포인트를 그림
     * 
     * @param g2 그래픽 컨텍스트
     * @param node 현재 노드
     */
    private void drawAllPoints(Graphics2D g2, Node node) {
        if (node == null) return;
        
        if (node.isLeaf()) {
            // 리프 노드: 포인트들을 검은색 작은 점으로 그리기
            g2.setColor(Color.BLACK);
            for (Point p : node.getPoints()) {
                drawPoint(g2, p, 4, false);
            }
        } else {
            // 내부 노드: 자식 노드들을 재귀적으로 탐색
            for (Node child : node.getChildren()) {
                drawAllPoints(g2, child);
            }
        }
    }
    
    /**
     * 사각형을 테두리만 그리는 메서드
     * 
     * @param g2 그래픽 컨텍스트
     * @param rect 그릴 사각형
     * @param lineWidth 선 두께
     */
    private void drawRectangle(Graphics2D g2, Rectangle rect, int lineWidth) {
        // 트리 좌표를 화면 좌표로 변환
        int x1 = toScreenX(rect.getLeftTop().getX());
        int y1 = toScreenY(rect.getLeftTop().getY());
        int x2 = toScreenX(rect.getRightBottom().getX());
        int y2 = toScreenY(rect.getRightBottom().getY());
        
        // 선 두께 설정
        g2.setStroke(new java.awt.BasicStroke(lineWidth));
        
        // 사각형 그리기 (왼쪽 위 좌표와 너비, 높이 사용)
        g2.drawRect(Math.min(x1, x2), Math.min(y1, y2), 
                   Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
    
    /**
     * 사각형을 채워서 그리는 메서드 (반투명 효과에 사용)
     * 
     * @param g2 그래픽 컨텍스트
     * @param rect 채울 사각형
     */
    private void fillRectangle(Graphics2D g2, Rectangle rect) {
        // 트리 좌표를 화면 좌표로 변환
        int x1 = toScreenX(rect.getLeftTop().getX());
        int y1 = toScreenY(rect.getLeftTop().getY());
        int x2 = toScreenX(rect.getRightBottom().getX());
        int y2 = toScreenY(rect.getRightBottom().getY());
        
        // 사각형 채우기
        g2.fillRect(Math.min(x1, x2), Math.min(y1, y2), 
                   Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
    
    /**
     * 포인트를 원으로 그리는 메서드
     * 
     * @param g2 그래픽 컨텍스트
     * @param p 그릴 포인트
     * @param size 원의 지름 (픽셀)
     * @param fill true면 채워진 원, false면 테두리만
     */
    private void drawPoint(Graphics2D g2, Point p, int size, boolean fill) {
        // 트리 좌표를 화면 좌표로 변환
        int x = toScreenX(p.getX());
        int y = toScreenY(p.getY());
        
        // 원 생성 (중심을 기준으로 size/2 만큼 이동하여 중앙 정렬)
        Ellipse2D.Double circle = new Ellipse2D.Double(
            x - size/2, 
            y - size/2, 
            size, 
            size
        );
        
        // 채우기 또는 테두리만 그리기
        if (fill) {
            g2.fill(circle);
        } else {
            g2.draw(circle);
        }
    }
    
    /**
     * 트리 좌표의 X를 화면 좌표로 변환
     * 
     * @param x 트리 좌표의 X
     * @return 화면 좌표의 X (픽셀)
     */
    private int toScreenX(double x) {
        return (int) (PADDING + x * SCALE);
    }
    
    /**
     * 트리 좌표의 Y를 화면 좌표로 변환
     * Y축은 화면과 반대 방향이므로 뒤집어야 함
     * 
     * @param y 트리 좌표의 Y
     * @return 화면 좌표의 Y (픽셀)
     */
    private int toScreenY(double y) {
        // getHeight()는 패널의 높이
        // Y축을 뒤집어서 수학적 좌표계로 변환 (아래가 0, 위가 양수)
        return (int) (getHeight() - PADDING - y * SCALE);
    }
}
