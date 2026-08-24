package com.mobicore.core.model;

/** One entry of the {@code MIDlet-<n>} attribute: display name, icon, class. */
public final class MidletEntry {

    private final int index;
    private final String name;
    private final String iconPath;
    private final String className;

    public MidletEntry(int index, String name, String iconPath, String className) {
        this.index = index;
        this.name = name;
        this.iconPath = iconPath;
        this.className = className;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    /** Icon resource inside the JAR, or {@code null} when the suite has none. */
    public String iconPath() {
        return iconPath;
    }

    public String className() {
        return className;
    }

    @Override
    public String toString() {
        return name + " (" + className + ")";
    }
}
