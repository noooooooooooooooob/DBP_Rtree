package org.dfpl.dbp.rtree.team2;

import java.util.List;

public class Rectangle {

	// 좌상단 포인트와 우하단 포인트로 표현
	private Point leftTop;
	private Point rightBottom;

	public Rectangle(Point p1, Point p2) {
		super();

		// p1, p2 중에서 leftTop과 rightBottom 결정
		double left = Math.min(p1.getX(), p2.getX());
		double top = Math.max(p1.getY(), p2.getY());
		double right = Math.max(p1.getX(), p2.getX());
		double bottom = Math.min(p1.getY(), p2.getY());

		this.leftTop = new Point(left, top);
		this.rightBottom = new Point(right, bottom);
	}

	public Point getLeftTop() {
		return leftTop;
	}

	public void setLeftTop(Point leftTop) {
		this.leftTop = leftTop;
	}

	public Point getRightBottom() {
		return rightBottom;
	}

	public void setRightBottom(Point rightBottom) {
		this.rightBottom = rightBottom;
	}

	@Override
	public String toString() {
		return "Rectangle [leftTop=(" + leftTop.getX() + "," + leftTop.getY() + "), rightBottom=(" + rightBottom.getX()
				+ "," + rightBottom.getY() + ")]";
	}

	// 넓이 반환하는 함수
	public double getArea() {
		return Math.abs((rightBottom.getX() - leftTop.getX()) * (rightBottom.getY() - leftTop.getY()));
	}

	/** 
	 * 두 Rectangle이 겹치는지 여부를 반환
	 */
	public boolean intersects(Rectangle other) {
		// 겹치지 않는 경우를 확인해 전부 맞지 않으면 true 반환
		// 겹치지 않는 조건 1. this의 오른쪽이 other의 왼쪽보다 왼쪽에 있는 경우
		if (this.rightBottom.getX() < other.leftTop.getX()) {
			return false;
		}

		// 겹치지 않는 조건 2. this의 왼쪽이 other의 오른쪽보다 오른쪽에 있는 경우
		if (this.leftTop.getX() > other.rightBottom.getX()) {
			return false;
		}

		// 겹치지 않는 조건 3. this의 아래쪽이 other의 위쪽보다 위에 있는 경우
		if (this.rightBottom.getY() > other.leftTop.getY()) {
			return false;
		}
		// 겹치지 않는 조건 4. this의 위쪽이 other의 아래쪽보다 아래에 있는 경우
		if (this.leftTop.getY() < other.rightBottom.getY()) {
			return false;
		}

		// 두 사각형이 겹치는 경우
		return true;
	}

	/**
	 * 이 Rectangle이 Point를 포함하는지 여부 반환
	 */
	public boolean contains(Point point) {
		if (this.leftTop.getX() <= point.getX() && this.rightBottom.getX() >= point.getX()
				&& this.leftTop.getY() >= point.getY() && this.rightBottom.getY() <= point.getY()) {
			return true;
		}
		return false;
	}

	/**
	 * 이 Rectangle이 Point를 포함하도록 확장한 새로운 Rectangle 반환
	 */
	public Rectangle expandToInclude(Point point) {
		double left = Math.min(this.leftTop.getX(), point.getX());
		double top = Math.max(this.leftTop.getY(), point.getY());
		double right = Math.max(this.rightBottom.getX(), point.getX());
		double bottom = Math.min(this.rightBottom.getY(), point.getY());

		return new Rectangle(new Point(left, top), new Point(right, bottom));
	}

	/**
	 * 이 Rectangle과 다른 Rectangle을 포함하는 새로운 Rectangle 반환
	 */
	public Rectangle expandToInclude(Rectangle other) {
		double left = Math.min(this.leftTop.getX(), other.leftTop.getX());
		double top = Math.max(this.leftTop.getY(), other.leftTop.getY());
		double right = Math.max(this.rightBottom.getX(), other.rightBottom.getX());
		double bottom = Math.min(this.rightBottom.getY(), other.rightBottom.getY());

		return new Rectangle(new Point(left, top), new Point(right, bottom));
	}

	/**
	 * 이 Rectangle과 Point간의 최소 거리 반환 (Rectangle 내부에 Point가 있으면 0)
	 */
	public double minDist(Point p) {
		double xMin = this.getLeftTop().getX();
		double yMax = this.getLeftTop().getY();
		double xMax = this.getRightBottom().getX();
		double yMin = this.getRightBottom().getY();

		// Point가 Rectangle 내부에 있으면 거리 0 반환
		double dx = 0.0;
		double dy = 0.0;

		// x축 기준으로 Rectangle 밖에 있으면 가장 가까운 거리 계산
		if (p.getX() < xMin)
			dx = xMin - p.getX();
		else if (p.getX() > xMax)
			dx = p.getX() - xMax;

		// y축 기준으로 Rectangle 밖에 있으면 가장 가까운 거리 계산
		if (p.getY() < yMin)
			dy = yMin - p.getY();
		else if (p.getY() > yMax)
			dy = p.getY() - yMax;

		return (dx == 0 && dy == 0) ? 0.0 : Math.sqrt(dx * dx + dy * dy);
	}

	/**
	 * Point 리스트를 모두 포함하는 Rectangle 생성
	 */
	public static Rectangle makeNewRectFromPoints(List<Point> points) {
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
	 * Node 리스트를 모두 포함하는 Rectangle 생성
	 */
	public static Rectangle makeNewRectFromNodes(List<Node> nodes) {
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

			// 왼쪽 위 좌표 처리
			minX = Math.min(minX, rect.getLeftTop().getX());
			maxY = Math.max(maxY, rect.getLeftTop().getY());

			// 오른쪽 아래 좌표 처리
			maxX = Math.max(maxX, rect.getRightBottom().getX());
			minY = Math.min(minY, rect.getRightBottom().getY());
		}

		// 최종 Rectangle 1개만 생성
		return new Rectangle(new Point(minX, maxY), new Point(maxX, minY));
	}
}
