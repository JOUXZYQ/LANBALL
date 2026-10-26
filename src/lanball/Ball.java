package lanball;

/*
 * The ball. It rolls, slows down, and bounces off the walls.
 * The goal mouth is the gap in the left and right walls between the posts.
 */
public class Ball extends Circle {
    static final double RADIUS = 8;
    static final double POST_TOP = 206,
            POST_BOTTOM = 334; // the whole ball fits between the posts here
    int goalHalf = 72;

    Ball() {
        super(RADIUS);
        x = 480;
        y = 270;
    }

    /** Moves the ball for h seconds. Friction takes about a third of its speed each second. */
    void roll(double h) {
        x += vx * h;
        y += vy * h;
        double friction = Math.pow(0.985, 30 * h);
        vx *= friction;
        vy *= friction;
        keepInWalls();
    }

    boolean inGoalMouth() {
        return y >= 270 - goalHalf + RADIUS && y <= 270 + goalHalf - RADIUS;
    }

    /**
     * The walls stop the ball. A fast ball bounces back at 80 % speed; a slow one just stops at the
     * wall.
     */
    void keepInWalls() {
        if (y < 8) {
            y = 8;
            vy = bounce(vy);
        }
        if (y > 532) {
            y = 532;
            vy = -bounce(-vy);
        }
        if (!inGoalMouth()) {
            if (x < 8) {
                x = 8;
                vx = bounce(vx);
            }
            if (x > 952) {
                x = 952;
                vx = -bounce(-vx);
            }
        }
    }

    /** v is negative when the ball moves into the wall. The answer never points into the wall. */
    static double bounce(double v) {
        if (v < -40) return -v * 0.8;
        return Math.max(v, 0);
    }

    /** 0 if Red scored (ball in the right goal), 1 if Blue scored (left goal), -1 if no goal. */
    int goalFor() {
        if (x > 968) return 0;
        if (x < -8) return 1;
        return -1;
    }
}
