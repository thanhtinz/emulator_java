import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the Material Symbols SVGs in {@code assets/icons} into
 * {@code tools/…/ui/IconData.java}.
 *
 * <p>The interface draws no icon of its own invention: every glyph on screen
 * is the same Material icon the Android build shows through
 * {@code Icons.Filled}, so the preview and the phone cannot drift apart, and
 * nobody has to guess what a hand-drawn shape was meant to be.</p>
 *
 * <p>Run offline, never at build time — the generated file is checked in, so
 * the emulator itself needs neither AWT nor an SVG parser.</p>
 *
 * <pre>javac -d build/codegen codegen/IconGen.java
 * java -cp build/codegen IconGen assets/icons tools/src/…/ui/IconData.java</pre>
 */
public final class IconGen {

    /** Edge of the stored alpha map. Every screen draws an icon smaller. */
    private static final int SIZE = 64;

    /** The SVG canvas Material icons are authored on. */
    private static final double VIEW_BOX = 24.0;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File target = new File(args[1]);

        File[] files = dir.listFiles();
        Arrays.sort(files);
        List<String> names = new ArrayList<String>();
        List<String> payloads = new ArrayList<String>();
        for (int i = 0; i < files.length; i++) {
            String file = files[i].getName();
            if (!file.endsWith(".svg")) {
                continue;
            }
            names.add(file.substring(0, file.length() - 4));
            payloads.add(encode(rasterise(read(files[i]))));
        }
        write(target, names, payloads);
        System.out.println(names.size() + " icons -> " + target);
    }

    // ------------------------------------------------------------ svg input

    private static final Pattern PATH = Pattern.compile("<path([^>]*)>");
    private static final Pattern D_ATTR = Pattern.compile("d=\"([^\"]*)\"");

    /**
     * Every filled path in the file, merged. Material icons carry a
     * {@code fill="none"} rectangle covering the whole canvas as a spacer;
     * drawing it would fill the icon in solid.
     */
    private static Path2D.Double read(File file) throws Exception {
        String svg = new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        Matcher tags = PATH.matcher(svg);
        while (tags.find()) {
            String attrs = tags.group(1);
            if (attrs.contains("fill=\"none\"")) {
                continue;
            }
            Matcher d = D_ATTR.matcher(attrs);
            if (d.find()) {
                path.append(parse(d.group(1)), false);
            }
        }
        return path;
    }

    /**
     * A minimal SVG path parser: move, line, horizontal, vertical, cubic,
     * smooth cubic and close, each in both cases. That is every command the
     * icon set uses — it has no arcs and no quadratics — and a parser that
     * accepts only what is there cannot silently mis-draw what is not.
     */
    private static Path2D.Double parse(String d) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        Tokens in = new Tokens(d);
        double x = 0, y = 0, startX = 0, startY = 0;
        double lastControlX = 0, lastControlY = 0;
        char previous = ' ';
        char command = ' ';
        while (in.hasNext()) {
            if (in.peekIsLetter()) {
                command = in.letter();
            } else if (command == 'M') {
                command = 'L';
            } else if (command == 'm') {
                command = 'l';
            }
            boolean relative = Character.isLowerCase(command);
            char op = Character.toUpperCase(command);
            switch (op) {
                case 'M': {
                    x = relative ? x + in.number() : in.number();
                    y = relative ? y + in.number() : in.number();
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    break;
                }
                case 'L': {
                    x = relative ? x + in.number() : in.number();
                    y = relative ? y + in.number() : in.number();
                    path.lineTo(x, y);
                    break;
                }
                case 'H': {
                    x = relative ? x + in.number() : in.number();
                    path.lineTo(x, y);
                    break;
                }
                case 'V': {
                    y = relative ? y + in.number() : in.number();
                    path.lineTo(x, y);
                    break;
                }
                case 'C': {
                    double c1x = relative ? x + in.number() : in.number();
                    double c1y = relative ? y + in.number() : in.number();
                    double c2x = relative ? x + in.number() : in.number();
                    double c2y = relative ? y + in.number() : in.number();
                    double ex = relative ? x + in.number() : in.number();
                    double ey = relative ? y + in.number() : in.number();
                    path.curveTo(c1x, c1y, c2x, c2y, ex, ey);
                    lastControlX = c2x;
                    lastControlY = c2y;
                    x = ex;
                    y = ey;
                    break;
                }
                case 'S': {
                    // The first control point mirrors the previous curve's
                    // second one, or sits on the current point if the last
                    // command was not a curve.
                    boolean afterCurve = previous == 'C' || previous == 'c'
                            || previous == 'S' || previous == 's';
                    double c1x = afterCurve ? 2 * x - lastControlX : x;
                    double c1y = afterCurve ? 2 * y - lastControlY : y;
                    double c2x = relative ? x + in.number() : in.number();
                    double c2y = relative ? y + in.number() : in.number();
                    double ex = relative ? x + in.number() : in.number();
                    double ey = relative ? y + in.number() : in.number();
                    path.curveTo(c1x, c1y, c2x, c2y, ex, ey);
                    lastControlX = c2x;
                    lastControlY = c2y;
                    x = ex;
                    y = ey;
                    break;
                }
                case 'A': {
                    // Elliptical arc. Material icons use these for anything
                    // with a genuine circle in it, and a parser that stops at
                    // the first one would keep sending someone back here for
                    // every other icon.
                    double rx = in.number();
                    double ry = in.number();
                    double rotation = in.number();
                    boolean largeArc = in.number() != 0;
                    boolean sweep = in.number() != 0;
                    double ex = relative ? x + in.number() : in.number();
                    double ey = relative ? y + in.number() : in.number();
                    arcTo(path, x, y, rx, ry, rotation, largeArc, sweep, ex, ey);
                    x = ex;
                    y = ey;
                    break;
                }
                case 'Z': {
                    path.closePath();
                    x = startX;
                    y = startY;
                    break;
                }
                default:
                    throw new IllegalArgumentException("unsupported path command: " + command);
            }
            previous = command;
        }
        return path;
    }

    /**
     * Appends an SVG elliptical arc to {@code path}.
     *
     * <p>SVG states an arc by where it ends; Java2D wants its centre, so this
     * is the endpoint-to-centre conversion from the SVG specification's
     * implementation notes, followed by an {@link Arc2D} appended to the
     * path.</p>
     */
    private static void arcTo(Path2D.Double path, double x, double y, double rx, double ry,
                              double rotation, boolean largeArc, boolean sweep,
                              double ex, double ey) {
        if (rx == 0 || ry == 0) {
            path.lineTo(ex, ey);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double angle = Math.toRadians(rotation % 360.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double dx2 = (x - ex) / 2.0;
        double dy2 = (y - ey) / 2.0;
        double x1 = cos * dx2 + sin * dy2;
        double y1 = -sin * dx2 + cos * dy2;

        // An arc whose radii cannot span the two points is scaled up until it
        // can, which is what the specification asks for rather than an error.
        double lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
        if (lambda > 1) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }

        double sign = largeArc == sweep ? -1 : 1;
        double numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double coefficient = sign * Math.sqrt(Math.max(0, numerator / denominator));
        double cx1 = coefficient * rx * y1 / ry;
        double cy1 = -coefficient * ry * x1 / rx;
        double cx = cos * cx1 - sin * cy1 + (x + ex) / 2.0;
        double cy = sin * cx1 + cos * cy1 + (y + ey) / 2.0;

        double startAngle = angleBetween(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
        double extent = angleBetween((x1 - cx1) / rx, (y1 - cy1) / ry,
                (-x1 - cx1) / rx, (-y1 - cy1) / ry);
        if (!sweep && extent > 0) {
            extent -= 2 * Math.PI;
        } else if (sweep && extent < 0) {
            extent += 2 * Math.PI;
        }

        // Java2D measures angles the other way round the circle.
        Arc2D.Double arc = new Arc2D.Double();
        arc.setArc(cx - rx, cy - ry, rx * 2, ry * 2,
                -Math.toDegrees(startAngle), -Math.toDegrees(extent), Arc2D.OPEN);
        AffineTransform spin = AffineTransform.getRotateInstance(angle, cx, cy);
        path.append(spin.createTransformedShape(arc), true);
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double length = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
        double value = Math.acos(Math.max(-1, Math.min(1, dot / length)));
        return ux * vy - uy * vx < 0 ? -value : value;
    }

    /** Splits path data into commands and numbers. */
    private static final class Tokens {
        private final String text;
        private int at;

        Tokens(String text) {
            this.text = text;
            skip();
        }

        boolean hasNext() {
            return at < text.length();
        }

        boolean peekIsLetter() {
            return Character.isLetter(text.charAt(at));
        }

        char letter() {
            char c = text.charAt(at++);
            skip();
            return c;
        }

        double number() {
            int start = at;
            if (at < text.length() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
                at++;
            }
            // "1.5.5" is two numbers in SVG: a second point starts the next
            // one, and there is no separator to say so.
            boolean seenPoint = false;
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c >= '0' && c <= '9') {
                    at++;
                } else if (c == '.' && !seenPoint) {
                    seenPoint = true;
                    at++;
                } else {
                    break;
                }
            }
            double value = Double.parseDouble(text.substring(start, at));
            skip();
            return value;
        }

        private void skip() {
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') {
                    at++;
                } else {
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------------- raster

    /** Coverage per pixel, 0..255, from an anti-aliased fill. */
    private static byte[] rasterise(Path2D.Double path) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(SIZE / VIEW_BOX, SIZE / VIEW_BOX);
        g.setColor(java.awt.Color.WHITE);
        g.fill(path);
        g.dispose();

        byte[] alpha = new byte[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                alpha[y * SIZE + x] = (byte) ((image.getRGB(x, y) >>> 24) & 0xFF);
            }
        }
        return alpha;
    }

    /**
     * Four bits of coverage per pixel, two pixels to a byte, Base64 for the
     * source file. Sixteen levels is past what the eye separates on an edge,
     * and it halves a file that is checked in.
     */
    private static String encode(byte[] alpha) {
        byte[] packed = new byte[alpha.length / 2];
        for (int i = 0; i < packed.length; i++) {
            int high = ((alpha[i * 2] & 0xFF) + 8) / 17;
            int low = ((alpha[i * 2 + 1] & 0xFF) + 8) / 17;
            packed[i] = (byte) ((Math.min(15, high) << 4) | Math.min(15, low));
        }
        return Base64.getEncoder().encodeToString(packed);
    }

    // -------------------------------------------------------------- output

    private static void write(File target, List<String> names, List<String> payloads)
            throws Exception {
        target.getParentFile().mkdirs();
        PrintWriter out = new PrintWriter(target, "UTF-8");
        out.println("package com.mobicore.tools.ui;");
        out.println();
        out.println("/**");
        out.println(" * Material icon coverage maps, generated by {@code codegen/IconGen.java}.");
        out.println(" *");
        out.println(" * <p>Sources are the Material Symbols SVGs in {@code assets/icons},");
        out.println(" * Apache 2.0 licensed, the same set the Android build draws from. Do not");
        out.println(" * edit this file: add an SVG and run the generator.</p>");
        out.println(" */");
        out.println("public final class IconData {");
        out.println();
        out.println("    /** Edge of every stored coverage map, in pixels. */");
        out.println("    public static final int SIZE = " + SIZE + ";");
        out.println();
        out.print("    public static final String[] NAMES = {");
        for (int i = 0; i < names.size(); i++) {
            out.print(i % 4 == 0 ? "\n            " : " ");
            out.print("\"" + names.get(i) + "\",");
        }
        out.println("\n    };");
        out.println();
        for (int i = 0; i < names.size(); i++) {
            out.println("    private static final String[] " + constant(names.get(i)) + " = {");
            String payload = payloads.get(i);
            for (int at = 0; at < payload.length(); at += 100) {
                out.println("            \"" + payload.substring(at,
                        Math.min(payload.length(), at + 100)) + "\",");
            }
            out.println("    };");
            out.println();
        }
        out.println("    /** Base64 of the packed coverage for the icon at {@code index}. */");
        out.println("    public static String data(int index) {");
        out.println("        switch (index) {");
        for (int i = 0; i < names.size(); i++) {
            out.println("            case " + i + ": return join(" + constant(names.get(i)) + ");");
        }
        out.println("            default: return null;");
        out.println("        }");
        out.println("    }");
        out.println();
        out.println("    private static String join(String[] parts) {");
        out.println("        StringBuilder text = new StringBuilder();");
        out.println("        for (int i = 0; i < parts.length; i++) {");
        out.println("            text.append(parts[i]);");
        out.println("        }");
        out.println("        return text.toString();");
        out.println("    }");
        out.println();
        out.println("    private IconData() {");
        out.println("    }");
        out.println("}");
        out.close();
    }

    private static String constant(String name) {
        return name.toUpperCase() + "_BITS";
    }

    private IconGen() {
    }
}
