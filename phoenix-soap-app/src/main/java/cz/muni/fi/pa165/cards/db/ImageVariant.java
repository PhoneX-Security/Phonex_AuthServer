package cz.muni.fi.pa165.cards.db;

/**
 * Created by IntelliJ IDEA.
 * User: bleble
 * Date: 28.11.2011
 * Time: 20:14
 */
public enum ImageVariant {

    FRONT_SMALL(210, true),
    FRONT_MEDIUM(324, true),
    FRONT_LARGE(900, true),
    FRONT_ORIGINAL(0, true),
    
    BACK_SMALL(210, false),
    BACK_MEDIUM(324, false),
    BACK_LARGE(900, false),
    BACK_ORIGINAL(0, false);

    private int width;
    private boolean front;

    ImageVariant(int width, boolean front) {
        this.width = width;
        this.front = front;
    }

    public int getWidth() {
        return width;
    }

    public boolean isFront() {
        return front;
    }
}
