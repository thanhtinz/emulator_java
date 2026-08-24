package com.mobicore.core.gfx;

/**
 * ARGB surface with the drawing primitives MIDP {@code Graphics} needs.
 *
 * <p>This is the single rendering implementation shared by the emulator screen,
 * off-screen {@code Image} buffers and the screenshot pipeline, so a game and a
 * screenshot can never disagree about what a frame looks like.</p>
 */
public final class Framebuffer {

    /** Source pixels replace destination pixels, alpha included. */
    public static final int BLEND_REPLACE = 0;
    /** Source pixels are alpha-blended over the destination. */
    public static final int BLEND_SRC_OVER = 1;

    private final int[] pixels;
    private final int width;
    private final int height;

    private int color = 0xFF000000;
    private int translateX;
    private int translateY;
    private int clipX;
    private int clipY;
    private int clipW;
    private int clipH;
    private int blendMode = BLEND_SRC_OVER;

    public Framebuffer(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Framebuffer must have a positive size");
        }
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        resetClip();
        fill(0xFF000000);
    }

    public static Framebuffer wrap(int[] pixels, int width, int height) {
        Framebuffer frame = new Framebuffer(width, height);
        System.arraycopy(pixels, 0, frame.pixels, 0, Math.min(pixels.length, frame.pixels.length));
        return frame;
    }

    public int[] pixels() {
        return pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return pixels[y * width + x];
    }

    public void fill(int argb) {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = argb;
        }
    }

    public Framebuffer copy() {
        return wrap(pixels, width, height);
    }

    // ---------------------------------------------------------------- state

    /** Sets the current colour. Values without an alpha byte are made opaque. */
    public void setColor(int argb) {
        this.color = (argb >>> 24) == 0 ? (0xFF000000 | argb) : argb;
    }

    public void setColor(int red, int green, int blue) {
        setColor(0xFF000000 | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF));
    }

    public int color() {
        return color;
    }

    public void setBlendMode(int mode) {
        this.blendMode = mode;
    }

    public int blendMode() {
        return blendMode;
    }

    public void translate(int dx, int dy) {
        translateX += dx;
        translateY += dy;
    }

    public void setTranslation(int x, int y) {
        translateX = x;
        translateY = y;
    }

    public int translateX() {
        return translateX;
    }

    public int translateY() {
        return translateY;
    }

    public void resetClip() {
        clipX = 0;
        clipY = 0;
        clipW = width;
        clipH = height;
    }

    /** Replaces the clip rectangle; coordinates are translated. */
    public void setClip(int x, int y, int w, int h) {
        int left = Math.max(0, x + translateX);
        int top = Math.max(0, y + translateY);
        int right = Math.min(width, x + translateX + w);
        int bottom = Math.min(height, y + translateY + h);
        clipX = left;
        clipY = top;
        clipW = Math.max(0, right - left);
        clipH = Math.max(0, bottom - top);
    }

    /** Intersects the clip rectangle with the given one. */
    public void clipRect(int x, int y, int w, int h) {
        int left = Math.max(clipX, x + translateX);
        int top = Math.max(clipY, y + translateY);
        int right = Math.min(clipX + clipW, x + translateX + w);
        int bottom = Math.min(clipY + clipH, y + translateY + h);
        clipX = left;
        clipY = top;
        clipW = Math.max(0, right - left);
        clipH = Math.max(0, bottom - top);
    }

    public int clipX() {
        return clipX - translateX;
    }

    public int clipY() {
        return clipY - translateY;
    }

    public int clipWidth() {
        return clipW;
    }

    public int clipHeight() {
        return clipH;
    }

    // ------------------------------------------------------------- painting

    /** Writes one device-space pixel, honouring the clip and the blend mode. */
    public void blend(int x, int y, int argb) {
        if (x < clipX || y < clipY || x >= clipX + clipW || y >= clipY + clipH) {
            return;
        }
        int index = y * width + x;
        int alpha = argb >>> 24;
        if (blendMode == BLEND_REPLACE || alpha == 0xFF) {
            pixels[index] = argb;
            return;
        }
        if (alpha == 0) {
            return;
        }
        int dst = pixels[index];
        int inverse = 255 - alpha;
        int outAlpha = alpha + (((dst >>> 24) * inverse) / 255);
        int r = (((argb >> 16) & 0xFF) * alpha + ((dst >> 16) & 0xFF) * inverse) / 255;
        int g = (((argb >> 8) & 0xFF) * alpha + ((dst >> 8) & 0xFF) * inverse) / 255;
        int b = ((argb & 0xFF) * alpha + (dst & 0xFF) * inverse) / 255;
        pixels[index] = (Math.min(255, outAlpha) << 24) | (r << 16) | (g << 8) | b;
    }

    public void setPixel(int x, int y) {
        blend(x + translateX, y + translateY, color);
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int left = Math.max(clipX, x + translateX);
        int top = Math.max(clipY, y + translateY);
        int right = Math.min(clipX + clipW, x + translateX + w);
        int bottom = Math.min(clipY + clipH, y + translateY + h);
        for (int py = top; py < bottom; py++) {
            for (int px = left; px < right; px++) {
                blend(px, py, color);
            }
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) {
            return;
        }
        drawLine(x, y, x + w, y);
        drawLine(x, y + h, x + w, y + h);
        drawLine(x, y, x, y + h);
        drawLine(x + w, y, x + w, y + h);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        int ax = x1 + translateX;
        int ay = y1 + translateY;
        int bx = x2 + translateX;
        int by = y2 + translateY;
        int dx = Math.abs(bx - ax);
        int dy = -Math.abs(by - ay);
        int sx = ax < bx ? 1 : -1;
        int sy = ay < by ? 1 : -1;
        int error = dx + dy;
        while (true) {
            blend(ax, ay, color);
            if (ax == bx && ay == by) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                ax += sx;
            }
            if (doubled <= dx) {
                error += dx;
                ay += sy;
            }
        }
    }

    public void fillRoundRect(int x, int y, int w, int h, int arcW, int arcH) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int rx = Math.max(0, Math.min(arcW / 2, w / 2));
        int ry = Math.max(0, Math.min(arcH / 2, h / 2));
        if (rx == 0 || ry == 0) {
            fillRect(x, y, w, h);
            return;
        }
        fillRect(x + rx, y, w - 2 * rx, h);
        fillRect(x, y + ry, rx, h - 2 * ry);
        fillRect(x + w - rx, y + ry, rx, h - 2 * ry);
        fillEllipseQuadrants(x + rx, y + ry, x + w - rx - 1, y + h - ry - 1, rx, ry);
    }

    private void fillEllipseQuadrants(int left, int top, int right, int bottom, int rx, int ry) {
        for (int dy = 0; dy <= ry; dy++) {
            double normalised = (double) dy / ry;
            int dx = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - normalised * normalised)));
            for (int px = 0; px <= dx; px++) {
                blendTranslated(left - px, top - dy);
                blendTranslated(right + px, top - dy);
                blendTranslated(left - px, bottom + dy);
                blendTranslated(right + px, bottom + dy);
            }
        }
    }

    private void blendTranslated(int x, int y) {
        blend(x + translateX, y + translateY, color);
    }

    public void drawRoundRect(int x, int y, int w, int h, int arcW, int arcH) {
        int rx = Math.max(0, Math.min(arcW / 2, w / 2));
        int ry = Math.max(0, Math.min(arcH / 2, h / 2));
        drawLine(x + rx, y, x + w - rx, y);
        drawLine(x + rx, y + h, x + w - rx, y + h);
        drawLine(x, y + ry, x, y + h - ry);
        drawLine(x + w, y + ry, x + w, y + h - ry);
        drawArcOutline(x, y, w, h, rx, ry);
    }

    private void drawArcOutline(int x, int y, int w, int h, int rx, int ry) {
        if (rx == 0 || ry == 0) {
            return;
        }
        for (int step = 0; step <= 90; step++) {
            double angle = Math.toRadians(step);
            int dx = (int) Math.round(rx * Math.cos(angle));
            int dy = (int) Math.round(ry * Math.sin(angle));
            blendTranslated(x + w - rx + dx, y + ry - dy);
            blendTranslated(x + rx - dx, y + ry - dy);
            blendTranslated(x + w - rx + dx, y + h - ry + dy);
            blendTranslated(x + rx - dx, y + h - ry + dy);
        }
    }

    /** Filled arc, matching the MIDP convention of degrees counter-clockwise from 3 o'clock. */
    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0 || arcAngle == 0) {
            return;
        }
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double rx = w / 2.0;
        double ry = h / 2.0;
        int from = Math.min(startAngle, startAngle + arcAngle);
        int to = Math.max(startAngle, startAngle + arcAngle);
        for (int py = y; py <= y + h; py++) {
            for (int px = x; px <= x + w; px++) {
                double nx = (px + 0.5 - cx) / rx;
                double ny = (cy - py - 0.5) / ry;
                if (nx * nx + ny * ny > 1.0) {
                    continue;
                }
                double degrees = Math.toDegrees(Math.atan2(ny, nx));
                if (degrees < 0) {
                    degrees += 360;
                }
                if (withinArc(degrees, from, to)) {
                    blendTranslated(px, py);
                }
            }
        }
    }

    private static boolean withinArc(double degrees, int from, int to) {
        if (to - from >= 360) {
            return true;
        }
        double normalisedFrom = ((from % 360) + 360) % 360;
        double span = to - from;
        double delta = degrees - normalisedFrom;
        while (delta < 0) {
            delta += 360;
        }
        return delta <= span;
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0) {
            return;
        }
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        int steps = Math.max(8, Math.abs(arcAngle));
        for (int i = 0; i <= steps; i++) {
            double degrees = startAngle + (double) arcAngle * i / steps;
            double radians = Math.toRadians(degrees);
            blendTranslated((int) Math.round(cx + Math.cos(radians) * w / 2.0),
                    (int) Math.round(cy - Math.sin(radians) * h / 2.0));
        }
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        int minX = Math.min(x1, Math.min(x2, x3));
        int maxX = Math.max(x1, Math.max(x2, x3));
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (insideTriangle(px, py, x1, y1, x2, y2, x3, y3)) {
                    blendTranslated(px, py);
                }
            }
        }
    }

    private static boolean insideTriangle(int px, int py, int x1, int y1, int x2, int y2, int x3, int y3) {
        int d1 = cross(px, py, x1, y1, x2, y2);
        int d2 = cross(px, py, x2, y2, x3, y3);
        int d3 = cross(px, py, x3, y3, x1, y1);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    private static int cross(int px, int py, int x1, int y1, int x2, int y2) {
        return (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
    }

    // --------------------------------------------------------------- images

    /** Draws an ARGB block at the given position. */
    public void drawPixels(int[] src, int srcWidth, int srcHeight, int x, int y) {
        drawRegion(src, srcWidth, srcHeight, 0, 0, srcWidth, srcHeight, x, y);
    }

    public void drawRegion(int[] src, int srcWidth, int srcHeight,
                           int sx, int sy, int sw, int sh, int dx, int dy) {
        for (int row = 0; row < sh; row++) {
            int sourceY = sy + row;
            if (sourceY < 0 || sourceY >= srcHeight) {
                continue;
            }
            for (int column = 0; column < sw; column++) {
                int sourceX = sx + column;
                if (sourceX < 0 || sourceX >= srcWidth) {
                    continue;
                }
                blend(dx + column + translateX, dy + row + translateY, src[sourceY * srcWidth + sourceX]);
            }
        }
    }

    public void drawFramebuffer(Framebuffer source, int x, int y) {
        drawPixels(source.pixels, source.width, source.height, x, y);
    }

    /**
     * Nearest-neighbour scale into a new surface. Classic games are pixel art,
     * so the default upscale must not smooth anything.
     */
    public Framebuffer scaleNearest(int targetWidth, int targetHeight) {
        Framebuffer target = new Framebuffer(targetWidth, targetHeight);
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = (int) ((long) y * height / targetHeight);
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = (int) ((long) x * width / targetWidth);
                target.pixels[y * targetWidth + x] = pixels[sourceY * width + sourceX];
            }
        }
        return target;
    }

    /** Largest integer scale that still fits inside the given viewport. */
    public int integerScaleFor(int viewportWidth, int viewportHeight) {
        int scale = Math.min(viewportWidth / width, viewportHeight / height);
        return Math.max(1, scale);
    }
}
