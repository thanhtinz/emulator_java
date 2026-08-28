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
                int targetX;
                int targetY;
                switch (transform) {
                    case MIRROR:
                        targetX = width - 1 - column;
                        targetY = row;
                        break;
                    case ROT180:
                        targetX = width - 1 - column;
                        targetY = height - 1 - row;
                        break;
                    case MIRROR_ROT180:
                        targetX = column;
                        targetY = height - 1 - row;
                        break;
                    case ROT90:
                        targetX = height - 1 - row;
                        targetY = column;
                        break;
                    case ROT270:
                        targetX = row;
                        targetY = width - 1 - column;
                        break;
                    // Lật trước, xoay sau — đúng thứ tự MIDP nói, và thứ tự
                    // ấy có thật: lật rồi xoay chín mươi độ không ra cùng kết
                    // quả với xoay rồi lật. Hai phép này từng làm ngược, nên
                    // chúng đổi chỗ cho nhau: một con thú quay mặt sang phải
                    // hiện ra quay sang trái.
                    case MIRROR_ROT90:
                        targetX = height - 1 - row;
                        targetY = width - 1 - column;
                        break;
                    case MIRROR_ROT270:
                        targetX = row;
                        targetY = column;
                        break;
                    default:
                        targetX = column;
                        targetY = row;
                        break;
                }
                out[targetY * outWidth + targetX] = pixel;
            }
        }
        return out;
    }
}
