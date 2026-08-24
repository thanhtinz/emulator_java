package com.mobicore.core.model;

import com.mobicore.core.storage.Json;

import java.util.Map;

/**
 * Settings that belong to the app rather than to any one game.
 *
 * <p>Kept apart from {@link GameProfile} on purpose: a game's settings are
 * about that game and travel with it in a backup, while these are about the
 * person using the phone.</p>
 */
public final class AppSettings {

    /** Light interface — the default, because most people play in daylight. */
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    /** Whatever the phone is set to, which is what most apps should do. */
    public static final int THEME_SYSTEM = 2;

    private int theme = THEME_LIGHT;
    /** Sort order the library opens with; see {@code GameLibrary.SORT_*}. */
    private int librarySort;
    private boolean confirmBeforeDeleting = true;

    public int theme() {
        return theme;
    }

    public void setTheme(int theme) {
        this.theme = theme < THEME_LIGHT || theme > THEME_SYSTEM ? THEME_LIGHT : theme;
    }

    /** The next theme in the cycle, for a one-tap toggle. */
    public int nextTheme() {
        return (theme + 1) % 3;
    }

    public static String themeName(int theme) {
        if (theme == THEME_DARK) {
            return "Tối";
        }
        return theme == THEME_SYSTEM ? "Theo hệ thống" : "Sáng";
    }

    public int librarySort() {
        return librarySort;
    }

    public void setLibrarySort(int librarySort) {
        this.librarySort = librarySort;
    }

    public boolean confirmBeforeDeleting() {
        return confirmBeforeDeleting;
    }

    public void setConfirmBeforeDeleting(boolean confirm) {
        this.confirmBeforeDeleting = confirm;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = Json.object();
        json.put("theme", Integer.valueOf(theme));
        json.put("themeName", themeName(theme));
        json.put("librarySort", Integer.valueOf(librarySort));
        json.put("confirmBeforeDeleting", Boolean.valueOf(confirmBeforeDeleting));
        return json;
    }

    public static AppSettings fromJson(Map<String, Object> json) {
        AppSettings settings = new AppSettings();
        settings.setTheme(Json.integer(json, "theme", THEME_LIGHT));
        settings.librarySort = Json.integer(json, "librarySort", 0);
        settings.confirmBeforeDeleting = Json.bool(json, "confirmBeforeDeleting", true);
        return settings;
    }
}
