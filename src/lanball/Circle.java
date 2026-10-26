package lanball;

/*
 * Anything round on the pitch: a position (x, y), a speed (vx, vy) and a radius.
 * Player and Ball both extend this class (Module 2: inheritance).
 */
public class Circle {
    double x, y, vx, vy;
    final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    double distanceTo(Circle other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    /** Keeps the centre inside a rectangle. */
    void keepInside(double left, double top, double right, double bottom) {
        x = Math.max(left, Math.min(right, x));
        y = Math.max(top, Math.min(bottom, y));
    }
}
