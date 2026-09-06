package com.mobicore.core.gfx;

/**
 * The eight sprite transforms MIDP defines.
 *
 * <p>Games rely on these constantly — one walk cycle is drawn facing right and
 * mirrored for left — so the operation is a straight index remap with no
 * filtering, which keeps pixel art exact.</p>
 */
public final class Transforms {

    public static final int NONE = 0;
    public static final int MIRROR_ROT180 = 1;
    public static final int MIRROR = 2;
    public static final int ROT180 = 3;
    public static final int MIRROR_ROT270 = 4;
    public static final int ROT90 = 5;
    public static final int ROT270 = 6;
    public static final int MIRROR_ROT90 = 7;

    private Transforms() {
    }

    /** True when the transform swaps width and height. */
    public static boolean swapsAxes(int transform) {
        return transform == ROT90 || transform == ROT270
                || transform == MIRROR_ROT90 || transform == MIRROR_ROT270;
    }

    public static int resultWidth(int transform, int width, int height) {
        return swapsAxes(transform) ? height : width;
    }

    public static int resultHeight(int transform, int width, int height) {
        return swapsAxes(transform) ? width : height;
    }

    /**
     * Copies a region out of {@code src} applying {@code transform}.
     *
     * @return a new pixel array of {@code resultWidth} x {@code resultHeight}
     */
    public static int[] apply(int[] src, int srcWidth, int srcHeight,
                              int x, int y, int width, int height, int transform) {
        int outWidth = resultWidth(transform, width, height);
        int outHeight = resultHeight(transform, width, height);
        int[] out = new int[outWidth * outHeight];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int sourceX = x + column;
                int sourceY = y + row;
                if (sourceX < 0 || sourceY < 0 || sourceX >= srcWidth || sourceY >= srcHeight) {
                    continue;
                }
                int pixel = src[sourceY * srcWidth + sourceX];
                int targetX = mapX(transform, width, height, column, row);
                int targetY = mapY(transform, width, height, column, row);
                out[targetY * outWidth + targetX] = pixel;
            }
        }
        return out;
    }

    /**
     * Where one point of a frame lands after the same transform.
     *
     * <p>The pixel loop above calls these rather than carrying its own copy of
     * the arithmetic. Two copies of eight cases is eight chances for a sprite
     * to be drawn one way and asked about another — and that mismatch is
     * exactly the kind that shows up as a character stepping sideways when it
     * turns around, which nobody can describe well enough to report.</p>
     *
     * @param width  the frame's width before the transform
     * @param height the frame's height before the transform
     */
    public static int mapX(int transform, int width, int height, int x, int y) {
        switch (transform) {
            case MIRROR: return width - 1 - x;
            case ROT180: return width - 1 - x;
            case MIRROR_ROT180: return x;
            case ROT90: return height - 1 - y;
            case ROT270: return y;
            case MIRROR_ROT90: return height - 1 - y;
            case MIRROR_ROT270: return y;
            default: return x;
        }
    }

    public static int mapY(int transform, int width, int height, int x, int y) {
        switch (transform) {
            case MIRROR: return y;
            case ROT180: return height - 1 - y;
            case MIRROR_ROT180: return height - 1 - y;
            case ROT90: return x;
            case ROT270: return width - 1 - x;
            case MIRROR_ROT90: return width - 1 - x;
            case MIRROR_ROT270: return x;
            default: return y;
        }
    }
}
