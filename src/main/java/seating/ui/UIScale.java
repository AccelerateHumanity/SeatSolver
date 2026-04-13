package seating.ui;

import java.awt.*;

/**
 * DPI-aware scaling utility. Detects the system display scaling factor
 * and provides helper methods for fonts and dimensions so the UI looks
 * consistent across 1080p (125%), 1440p (150%), and 4K (250%) displays.
 *
 * All hardcoded sizes in the UI layer should go through this class.
 */
public final class UIScale {

    /** Cached scale factor (1.0 = 96 DPI baseline). */
    private static final float SCALE;

    static {
        float s = 1.0f;
        try {
            int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
            s = dpi / 96.0f;
            // Clamp to sensible range
            if (s < 1.0f) s = 1.0f;
            if (s > 4.0f) s = 4.0f;
        } catch (Exception e) {
            // Headless or no toolkit — keep 1.0
        }
        SCALE = s;
    }

    private UIScale() {}

    /** Returns the display scale factor (e.g. 1.0, 1.25, 2.0, 2.5). */
    public static float scale() { return SCALE; }

    /** Scales a pixel/point value by the display factor. */
    public static int scaled(int value) {
        return Math.round(value * SCALE);
    }

    /** Creates a scaled Font. */
    public static Font font(String name, int style, int baseSize) {
        return new Font(name, style, scaled(baseSize));
    }

    /** Creates a scaled Dimension. */
    public static Dimension dimension(int width, int height) {
        return new Dimension(scaled(width), scaled(height));
    }
}
