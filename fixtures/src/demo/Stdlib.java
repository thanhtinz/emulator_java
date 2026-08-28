package demo;

/**
 * Every corner of the standard library a game reaches for.
 *
 * <p>One missing method is not a small thing here: CLDC has no reflection and
 * no fallbacks, so a game calling one that is not there stops at that line.
 * This walks the corners that were missing and hands back what each one gave,
 * so a single golden string covers the lot.</p>
 */
public final class Stdlib {

    public static String everything() throws Exception {
        StringBuffer out = new StringBuffer();
        out.append("\"  a  \".trim()=").append("  a  ".trim()).append('\n');
        out.append("\"AB\".toLowerCase()=").append("AB".toLowerCase()).append('\n');
        out.append("\"aXb\".replace('X', 'Y')=").append("aXb".replace('X', 'Y')).append('\n');
        out.append("\"abc\".compareTo(\"abd\")=").append("" + "abc".compareTo("abd")).append('\n');
        out.append("\"a,b,c\".indexOf(',', 2)=").append("" + "a,b,c".indexOf(',', 2)).append('\n');
        out.append("\"Hello\".regionMatches(true, 0, \"HELL\", 0, 4)=").append("" + "Hello".regionMatches(true, 0, "HELL", 0, 4)).append('\n');
        out.append("\"Hello\".regionMatches(0, \"Hell\", 0, 4)=").append("" + "Hello".regionMatches(0, "Hell", 0, 4)).append('\n');
        out.append("String.valueOf(chars, 1, 2)=").append(String.valueOf(new char[]{'h', 'i', '!'}, 1, 2)).append('\n');
        out.append("new String(bytes, \"UTF-8\")=").append(new String("Ch\u00e0o".getBytes("UTF-8"), "UTF-8")).append('\n');
        out.append("sb.delete(1, 3)=").append(new StringBuffer("abcd").delete(1, 3).toString()).append('\n');
        out.append("sb.setCharAt(0, Z)=").append(setCharAt()).append('\n');
        out.append("Integer.toString(255, 16)=").append(Integer.toString(255, 16)).append('\n');
        out.append("Integer.toOctalString(9)=").append(Integer.toOctalString(9)).append('\n');
        out.append("Long.toString(255, 16)=").append(Long.toString(255L, 16)).append('\n');
        out.append("Long.parseLong(\"ff\", 16)=").append("" + Long.parseLong("ff", 16)).append('\n');
        out.append("Math.round(2.6f)=").append("" + Math.round(2.6f)).append('\n');
        out.append("Math.round(-2.6d)=").append("" + Math.round(-2.6d)).append('\n');
        out.append("Character.isLowerCase(a)=").append("" + Character.isLowerCase('a')).append('\n');
        out.append("Character.isUpperCase(a)=").append("" + Character.isUpperCase('a')).append('\n');
        out.append("Vector.lastIndexOf=").append(vectorLastIndexOf()).append('\n');
        out.append("Vector.setSize=").append(vectorSetSize()).append('\n');
        out.append("Hashtable.contains(value)=").append(hashtableContains()).append('\n');
        out.append("Short/Byte=").append("" + (new Short((short) 7).shortValue() + new Byte((byte) 3).byteValue())).append('\n');
        out.append("\"x\".concat(\"y\").intern()=").append("x".concat("y").intern()).append('\n');
        return out.toString();
    }

    private static String setCharAt() {
        StringBuffer buffer = new StringBuffer("abcd");
        buffer.setCharAt(0, 'Z');
        return buffer.toString();
    }

    private static String vectorLastIndexOf() {
        java.util.Vector items = new java.util.Vector();
        items.addElement("a");
        items.addElement("b");
        items.addElement("a");
        return "" + items.lastIndexOf("a") + items.lastIndexOf("z");
    }

    private static String vectorSetSize() {
        java.util.Vector items = new java.util.Vector();
        items.addElement("a");
        items.setSize(3);
        String grown = items.size() + "," + items.elementAt(2);
        items.setSize(1);
        return grown + "," + items.size();
    }

    private static String hashtableContains() {
        java.util.Hashtable table = new java.util.Hashtable();
        table.put("k", "v");
        // contains hỏi về giá trị, containsKey hỏi về khoá.
        return "" + table.contains("v") + table.contains("k") + table.containsKey("k");
    }
}
