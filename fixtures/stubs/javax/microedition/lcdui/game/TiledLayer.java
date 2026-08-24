package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Compile-time stub; the emulator implements this natively. */
public class TiledLayer extends Layer {

    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
    }

    public void setCell(int column, int row, int tileIndex) {
    }

    public int getCell(int column, int row) {
        return 0;
    }

    public void fillCells(int column, int row, int columns, int rows, int tileIndex) {
    }

    public int createAnimatedTile(int staticTileIndex) {
        return 0;
    }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) {
    }

    public int getAnimatedTile(int animatedTileIndex) {
        return 0;
    }

    public final int getCellWidth() {
        return 0;
    }

    public final int getCellHeight() {
        return 0;
    }

    public final int getColumns() {
        return 0;
    }

    public final int getRows() {
        return 0;
    }

    public void paint(Graphics g) {
    }
}
