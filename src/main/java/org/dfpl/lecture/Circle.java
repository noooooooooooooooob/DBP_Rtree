package org.dfpl.lecture;

public class Circle {
    float x;
    float y;
    float r;

    public Circle(float x, float y, float r) {
        this.x = x;
        this.y = y;
        this.r = r;
    }
    float getArea()
    {
        return 3.14f * r * r;
    }
}
