package org.dfpl.dbp.rtree.team2;

public class Rectangle {

	// 좌상단 포인트와 우하단 포인트로 표현
	private Point leftTop;
	private Point rightBottom;

	public Rectangle(Point leftTop, Point rightBottom) {
		super();

		this.leftTop = new Point(Math.min(leftTop.getX(), rightBottom.getX()), Math.max(leftTop.getY(), rightBottom.getY()));
		this.rightBottom = new Point(Math.max(leftTop.getX(), rightBottom.getX()), Math.min(leftTop.getY(), rightBottom.getY()));
	}
    public Rectangle(){
        super();
        this.leftTop = new Point(0, 0);
        this.rightBottom = new Point(0, 0);
    }
    public Rectangle copy() {
        return new Rectangle(leftTop.copy(), rightBottom.copy());
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

    public double getXMin(){return leftTop.getX();}
    public double getXMax(){return rightBottom.getX();}
    public double getYMax(){return leftTop.getY();}
    public double getYMin(){return rightBottom.getY();}

	@Override
	public String toString() {
		return "Rectangle [leftTop=(" + leftTop.getX() + "," + leftTop.getY() + "), rightBottom=(" + rightBottom.getX()
				+ "," + rightBottom.getY() + ")]";
	}

    public void include(Rectangle r){
        Point newLt = new Point(Math.min(getXMin(),r.getXMin()), Math.max(getYMax(),r.getYMax()));
        setLeftTop(newLt);
        Point newRb = new Point(Math.max(getXMax(),r.getXMax()), Math.min(getYMin(),r.getYMin()));
        setRightBottom(newRb);
    }
    public void include(Point p){
        var x = p.getX();
        var y = p.getY();
        Point newLt = new Point(Math.min(getXMin(),x), Math.max(getYMax(),y));
        setLeftTop(newLt);
        Point newRb = new Point(Math.max(getXMax(),x), Math.min(getYMin(),y));
        setRightBottom(newRb);
    }
    public double getArea(){
        return (getXMax()-getXMin())*(getYMax()-getYMin());
    }

    // 해당 점이 사각형 내부에 있는지 확인
    public boolean contains(Point p)
    {
        var x = p.getX();
        var y = p.getY();
        return x >= getXMin() && x <= getXMax() && y >= getYMin() && y <= getYMax();
    }
}
