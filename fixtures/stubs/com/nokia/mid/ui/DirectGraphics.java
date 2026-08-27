package com.nokia.mid.ui;

import javax.microedition.lcdui.Image;

/** Stub for javac only; the emulator supplies this class natively. */
public interface DirectGraphics {

    int TYPE_INT_888_RGB = 0x0888;
    int TYPE_INT_8888_ARGB = 0x8888;
    int ROTATE_90 = 90;
    int ROTATE_180 = 180;
    int ROTATE_270 = 270;
    int FLIP_HORIZONTAL = 0x2000;
    int FLIP_VERTICAL = 0x4000;

    void setARGBColor(int argb);

    int getARGBColor();

    void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3);

    void drawPolygon(int[] xs, int xOffset, int[] ys, int yOffset, int points, int argb);

    void fillPolygon(int[] xs, int xOffset, int[] ys, int yOffset, int points, int argb);

    void drawPixels(int[] pixels, boolean transparency, int offset, int scanLength,
                    int x, int y, int width, int height, int manipulation, int format);

    void getPixels(int[] pixels, int offset, int scanLength, int x, int y, int width, int height,
                   int format);

    void drawImage(Image image, int x, int y, int anchor, int manipulation);
}
