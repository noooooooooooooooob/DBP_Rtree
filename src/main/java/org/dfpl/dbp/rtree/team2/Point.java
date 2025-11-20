package org.dfpl.dbp.rtree.team2;

public class Point {
	private double x;
	private double y;

	private static final double epsilon = 1e-9;

	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double distance(Point other) {
		double dx = this.x - other.getX();
		double dy = this.y - other.getY();
		return Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	public String toString() {
		return "Point [x=" + x + ", y=" + y + "]";
	}

	/**
	 * 두 Point가 거의 같은지 여부를 반환
	 */
	public boolean ApproxEquals(Point other) {
		return Math.abs(this.x - other.getX()) <= epsilon && Math.abs(this.y - other.getY()) <= epsilon;
	}

}
