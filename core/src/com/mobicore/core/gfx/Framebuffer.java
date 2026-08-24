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
    private boolean antialias;

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

    /**
     * Smooths the edges of diagonal and curved shapes.
     *
     * <p>MIDP itself had no anti-aliasing, and on a two inch screen it did not
     * need any. Shown several times larger on a modern display, the same
     * staircases are impossible to miss, so the emulator offers to soften them.
     * Axis-aligned rectangles are always drawn exactly; there is nothing to
     * smooth and softening them would only blur a HUD.</p>
     *
     * <p>Deliberately not applied to off-screen images a game draws into: many
     * suites build sprite sheets on a key colour and then make that exact
     * colour transparent, and blended edge pixels would leave a halo.</p>
     */
    public void setAntialias(boolean antialias) {
        this.antialias = antialias;
    }

    public boolean antialias() {
        return antialias;
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

    /** Blends one pixel in user space, honouring translation and clip. */
    public void blendPixel(int x, int y, int argb) {
        blend(x + translateX, y + translateY, argb);
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int left = Math.max(clipX, x + translateX);
        int top = Math.max(clipY, y + translateY);
        int right = Math.min(clipX + clipW, x + translateX + w);
        int bottom = Math.min(clipY + clipH, y + translateY + h);
        if (right <= left) {
            return;
        }
        // An opaque fill is a memory fill, and a game clears its whole screen
        // this way every frame: going through the per-pixel blend for it cost
        // more than everything else the frame did.
        if (blendMode == BLEND_REPLACE || (color >>> 24) == 0xFF) {
            for (int py = top; py < bottom; py++) {
                int row = py * width;
                java.util.Arrays.fill(pixels, row + left, row + right, color);
            }
            return;
        }
        if ((color >>> 24) == 0) {
            return;
        }
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
        if (antialias && x1 != x2 && y1 != y2) {
            drawLineSmooth(x1, y1, x2, y2);
            return;
        }
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

    /**
     * Xiaolin Wu's line: each step lights the two pixels straddling the ideal
     * line, weighted by how close the line passes to each.
     */
    private void drawLineSmooth(int x1, int y1, int x2, int y2) {
        double ax = x1;
        double ay = y1;
        double bx = x2;
        double by = y2;
        boolean steep = Math.abs(by - ay) > Math.abs(bx - ax);
        if (steep) {
            double swap = ax; ax = ay; ay = swap;
            swap = bx; bx = by; by = swap;
        }
        if (ax > bx) {
            double swap = ax; ax = bx; bx = swap;
            swap = ay; ay = by; by = swap;
        }
        double gradient = bx == ax ? 1 : (by - ay) / (bx - ax);
        double intersect = ay;
        for (int x = (int) Math.round(ax); x <= (int) Math.round(bx); x++) {
            int base = (int) Math.floor(intersect);
            double fraction = intersect - base;
            plotCoverage(steep, x, base, 1 - fraction);
            plotCoverage(steep, x, base + 1, fraction);
            intersect += gradient;
        }
    }

    private void plotCoverage(boolean steep, int major, int minor, double coverage) {
        if (coverage <= 0.004) {
            return;
        }
        int x = steep ? minor : major;
        int y = steep ? major : minor;
        blendTranslated(x, y, coverage);
    }

    /** Blends the current colour at a user-space pixel, scaled by coverage. */
    private void blendTranslated(int x, int y, double coverage) {
        int alpha = (int) Math.round((color >>> 24) * Math.min(1.0, Math.max(0.0, coverage)));
        if (alpha <= 0) {
            return;
        }
        blend(x + translateX, y + translateY, (alpha << 24) | (color & 0xFFFFFF));
    }

    /** A shape's inside test, sampled at sub-pixel positions for coverage. */
    private interface Region {
        boolean contains(double x, double y);
    }

    /**
     * Fills a region by sampling each pixel on a 4x4 grid. Sixteen samples is
     * plenty for the shapes MIDP offers and costs nothing noticeable on a
     * screen this size.
     */
    /**
     * Fills a shape with anti-aliased edges.
     *
     * <p>Sixteen samples a pixel is what smooth edges cost, but only an edge
     * needs them: a pixel with all four corners inside the shape is wholly
     * inside it, and one with all four outside — and its centre outside too —
     * is not in it at all. Testing the corners first turns a full-screen
     * triangle from sixteen tests a pixel into four for nearly all of it,
     * which is most of what a game's drawing costs.</p>
     */
    private void fillRegion(int left, int top, int right, int bottom, Region region) {
        for (int py = top; py <= bottom; py++) {
            for (int px = left; px <= right; px++) {
                boolean topLeft = region.contains(px, py);
                boolean topRight = region.contains(px + 1, py);
                boolean bottomLeft = region.contains(px, py + 1);
                boolean bottomRight = region.contains(px + 1, py + 1);
                if (topLeft && topRight && bottomLeft && bottomRight) {
                    blendTranslated(px, py, 1.0);
                    continue;
                }
                if (!topLeft && !topRight && !bottomLeft && !bottomRight
                        && !region.contains(px + 0.5, py + 0.5)) {
                    // Every corner and the centre are outside. A shape thin
                    // enough to slip between them is thinner than a pixel,
                    // and the samples below would find at most a trace of it.
                    continue;
                }
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (region.contains(px + (sx + 0.5) / 4.0, py + (sy + 0.5) / 4.0)) {
                            hits++;
                        }
                    }
                }
                if (hits == 16) {
                    blendTranslated(px, py, 1.0);
                } else if (hits > 0) {
                    blendTranslated(px, py, hits / 16.0);
                }
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

    private void fillEllipseQuadrants(int left, int top, int right, int bottom,
                                      final int rx, final int ry) {
        corner(left, top, rx, ry, -1, -1);
        corner(right, top, rx, ry, 1, -1);
        corner(left, bottom, rx, ry, -1, 1);
        corner(right, bottom, rx, ry, 1, 1);
    }

    /** One rounded corner, as a quarter ellipse centred on the corner pixel. */
    private void corner(final int cx, final int cy, final int rx, final int ry,
                        final int dirX, final int dirY) {
        final double centreX = cx + 0.5;
        final double centreY = cy + 0.5;
        Region quarter = new Region() {
            public boolean contains(double x, double y) {
                double nx = (x - centreX) / rx;
                double ny = (y - centreY) / ry;
                if (nx * dirX < 0 || ny * dirY < 0) {
                    return false;
                }
                return nx * nx + ny * ny <= 1.0;
            }
        };
        int left = dirX < 0 ? cx - rx : cx;
        int right = dirX < 0 ? cx : cx + rx;
        int top = dirY < 0 ? cy - ry : cy;
        int bottom = dirY < 0 ? cy : cy + ry;
        if (antialias) {
            fillRegion(left, top, right, bottom, quarter);
            return;
        }
        for (int py = top; py <= bottom; py++) {
            for (int px = left; px <= right; px++) {
                if (quarter.contains(px + 0.5, py + 0.5)) {
                    blendTranslated(px, py, 1.0);
                }
            }
        }
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
        int steps = antialias ? 360 : 90;
        for (int step = 0; step <= steps; step++) {
            double angle = Math.toRadians(step * 90.0 / steps);
            double dx = rx * Math.cos(angle);
            double dy = ry * Math.sin(angle);
            plotOutline(x + w - rx + dx, y + ry - dy);
            plotOutline(x + rx - dx, y + ry - dy);
            plotOutline(x + w - rx + dx, y + h - ry + dy);
            plotOutline(x + rx - dx, y + h - ry + dy);
        }
    }

    private void plotOutline(double x, double y) {
        if (antialias) {
            spreadPoint(x + 0.5, y + 0.5);
        } else {
            blendTranslated((int) Math.round(x), (int) Math.round(y), 1.0);
        }
    }

    /** Filled arc, matching the MIDP convention of degrees counter-clockwise from 3 o'clock. */
    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0 || arcAngle == 0) {
            return;
        }
        final double cx = x + w / 2.0;
        final double cy = y + h / 2.0;
        final double rx = w / 2.0;
        final double ry = h / 2.0;
        final int from = Math.min(startAngle, startAngle + arcAngle);
        final int to = Math.max(startAngle, startAngle + arcAngle);
        Region wedge = new Region() {
            public boolean contains(double px, double py) {
                double nx = (px - cx) / rx;
                double ny = (cy - py) / ry;
                if (nx * nx + ny * ny > 1.0) {
                    return false;
                }
                double degrees = Math.toDegrees(Math.atan2(ny, nx));
                if (degrees < 0) {
                    degrees += 360;
                }
                return withinArc(degrees, from, to);
            }
        };
        if (antialias) {
            fillRegion(x, y, x + w, y + h, wedge);
            return;
        }
        for (int py = y; py <= y + h; py++) {
            for (int px = x; px <= x + w; px++) {
                if (wedge.contains(px + 0.5, py + 0.5)) {
                    blendTranslated(px, py, 1.0);
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
        int steps = Math.max(8, Math.abs(arcAngle) * (antialias ? 4 : 1));
        for (int i = 0; i <= steps; i++) {
            double degrees = startAngle + (double) arcAngle * i / steps;
            double radians = Math.toRadians(degrees);
            double px = cx + Math.cos(radians) * w / 2.0;
            double py = cy - Math.sin(radians) * h / 2.0;
            if (antialias) {
                spreadPoint(px, py);
            } else {
                blendTranslated((int) Math.round(px), (int) Math.round(py), 1.0);
            }
        }
    }

    /** Spreads one sub-pixel sample over the four pixels it straddles. */
    private void spreadPoint(double x, double y) {
        int px = (int) Math.floor(x - 0.5);
        int py = (int) Math.floor(y - 0.5);
        double fx = x - 0.5 - px;
        double fy = y - 0.5 - py;
        blendTranslated(px, py, (1 - fx) * (1 - fy));
        blendTranslated(px + 1, py, fx * (1 - fy));
        blendTranslated(px, py + 1, (1 - fx) * fy);
        blendTranslated(px + 1, py + 1, fx * fy);
    }

    public void fillTriangle(final int x1, final int y1, final int x2, final int y2,
                             final int x3, final int y3) {
        int minX = Math.min(x1, Math.min(x2, x3));
        int maxX = Math.max(x1, Math.max(x2, x3));
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        Region triangle = new Region() {
            public boolean contains(double x, double y) {
                return insideTriangle(x, y, x1, y1, x2, y2, x3, y3);
            }
        };
        if (antialias) {
            fillRegion(minX, minY, maxX, maxY, triangle);
            return;
        }
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (triangle.contains(px + 0.5, py + 0.5)) {
                    blendTranslated(px, py, 1.0);
                }
            }
        }
    }

    private static boolean insideTriangle(double px, double py, int x1, int y1,
                                          int x2, int y2, int x3, int y3) {
        double d1 = cross(px, py, x1, y1, x2, y2);
        double d2 = cross(px, py, x2, y2, x3, y3);
        double d3 = cross(px, py, x3, y3, x1, y1);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    private static double cross(double px, double py, int x1, int y1, int x2, int y2) {
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

    /**
     * Bilinear scale into a new surface.
     *
     * <p>This is the default for showing an emulated screen. A handset packed
     * 240x320 into about two inches, so the pixels were far too small to see;
     * blowing the same image up on a modern display with nearest-neighbour
     * turns each of them into a visible block, which looks far more pixelated
     * than the original hardware ever did.</p>
     */
    public Framebuffer scaleSmooth(int targetWidth, int targetHeight) {
        return scaleSmoothInto(new Framebuffer(targetWidth, targetHeight));
    }

    /**
     * The same, into a surface the caller keeps.
     *
     * <p>This runs once per frame for the whole screen, so it is written in
     * fixed point with the horizontal sample positions worked out once per
     * call rather than once per pixel. The arithmetic was floating point and
     * per channel, which cost more than everything the game itself did in the
     * same frame. Reusing the target matters as much: a 480x640 surface is
     * over a megabyte, and allocating one per frame gave the garbage
     * collector more work than the emulator.</p>
     */
    public Framebuffer scaleSmoothInto(Framebuffer target) {
        int targetWidth = target.width;
        int targetHeight = target.height;
        if (targetWidth <= 0 || targetHeight <= 0 || width <= 0 || height <= 0) {
            return target;
        }

        // Sample at pixel centres, so the edges of the image are not stretched
        // half a source pixel outwards. Positions are 16.16 fixed point.
        long stepX = ((long) width << 16) / targetWidth;
        long stepY = ((long) height << 16) / targetHeight;

        int[] leftOf = new int[targetWidth];
        int[] rightOf = new int[targetWidth];
        int[] weightOf = new int[targetWidth];
        for (int x = 0; x < targetWidth; x++) {
            long sourceX = (x * stepX) + (stepX >> 1) - (1 << 15);
            int x0 = (int) (sourceX >> 16);
            leftOf[x] = clamp(x0, width - 1);
            rightOf[x] = clamp(x0 + 1, width - 1);
            weightOf[x] = sourceX < 0 ? 0 : (int) ((sourceX >> 8) & 0xFF);
        }

        int[] out = target.pixels;
        for (int y = 0; y < targetHeight; y++) {
            long sourceY = (y * stepY) + (stepY >> 1) - (1 << 15);
            int y0 = (int) (sourceY >> 16);
            int weightY = sourceY < 0 ? 0 : (int) ((sourceY >> 8) & 0xFF);
            int topRow = clamp(y0, height - 1) * width;
            int bottomRow = clamp(y0 + 1, height - 1) * width;
            int row = y * targetWidth;
            for (int x = 0; x < targetWidth; x++) {
                int left = leftOf[x];
                int right = rightOf[x];
                int weightX = weightOf[x];
                int top = lerp(pixels[topRow + left], pixels[topRow + right], weightX);
                int bottom = lerp(pixels[bottomRow + left], pixels[bottomRow + right], weightX);
                out[row + x] = lerp(top, bottom, weightY);
            }
        }
        return target;
    }

    /**
     * Blend of two ARGB pixels, {@code weight} running 0..255 towards
     * {@code b}. Per channel on purpose: packing two channels into one
     * multiply is faster still, but goes wrong the moment a difference is
     * negative, and a wrong colour is worse than a slower one.
     */
    private static int lerp(int a, int b, int weight) {
        if (weight == 0) {
            return a;
        }
        int alpha = ((a >>> 24) & 0xFF) + ((((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * weight >> 8);
        int red = ((a >>> 16) & 0xFF) + ((((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * weight >> 8);
        int green = ((a >>> 8) & 0xFF) + ((((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * weight >> 8);
        int blue = (a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * weight >> 8);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int value, int max) {
        return value < 0 ? 0 : (value > max ? max : value);
    }

    /** Largest integer scale that still fits inside the given viewport. */
    public int integerScaleFor(int viewportWidth, int viewportHeight) {
        int scale = Math.min(viewportWidth / width, viewportHeight / height);
        return Math.max(1, scale);
    }
}
