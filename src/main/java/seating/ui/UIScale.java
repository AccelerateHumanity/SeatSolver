package seating.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * DPI-aware scaling utility. Detects whether the JVM is already applying
 * HiDPI scaling (Java 9+ native behavior on Windows/macOS) and, if so,
 * stays out of the way. Only falls back to manual scaling on older JVMs
 * that don't auto-scale the UI.
 *
 * <p>All hardcoded sizes in the UI layer go through this class. On
 * modern Java + Windows, this is effectively a no-op — the JVM handles
 * all HiDPI scaling for fonts, dimensions, and Graphics2D rendering.
 * Applying additional scaling on top of that would double-scale and
 * break layout (see v1.3.0 4K@250% bug).
 */
public final class UIScale {

    /** Cached scale factor (1.0 = no manual scaling needed). */
    private static final float SCALE = detectScale();

    private UIScale() {}

    /**
     * Decides whether we need to apply a manual scale factor.
     * Returns 1.0 if the JVM is already auto-scaling (Java 9+ DPI-aware mode).
     * Only returns > 1.0 on older JVMs where we must compensate ourselves.
     */
    private static float detectScale() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            if (ge.isHeadlessInstance()) return 1.0f;

            // Java 9+ exposes the effective HiDPI scale via the default
            // GraphicsConfiguration's transform. When scaleX > 1, the JVM
            // is already rendering everything at the scaled size —
            // applying our own multiplier on top would double-scale.
            GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            AffineTransform tx = gc.getDefaultTransform();
            double javaScale = tx.getScaleX();
            if (javaScale > 1.01) return 1.0f;

            // Fallback for Java 8 / older toolkits that don't auto-scale:
            // derive a scale factor from the Toolkit-reported DPI.
            int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
            float s = dpi / 96.0f;
            if (s < 1.0f) s = 1.0f;
            if (s > 4.0f) s = 4.0f;
            return s;
        } catch (Exception e) {
            return 1.0f; // safe default
        }
    }

    /** Returns the display scale factor (1.0 on modern Java, >1 on older JVMs). */
    public static float scale() { return SCALE; }

    /** Scales a pixel/point value by the display factor. */
    public static int scaled(int value) {
        return Math.round(value * SCALE);
    }

    /** Creates a (possibly scaled) Font. */
    public static Font font(String name, int style, int baseSize) {
        return new Font(name, style, scaled(baseSize));
    }

    /** Creates a (possibly scaled) Dimension. */
    public static Dimension dimension(int width, int height) {
        return new Dimension(scaled(width), scaled(height));
    }
}
