package com.mobicore.tests;

import com.mobicore.core.model.GameProfile;
import com.mobicore.core.model.KeypadArrangement;
import com.mobicore.core.model.KeypadPlan;
import com.mobicore.core.storage.Json;

import java.util.Map;

/**
 * The three keypads, measured rather than looked at.
 *
 * <p>A keypad is judged with a thumb, and a thumb cannot be put in a test. But
 * the two ways a keypad fails outright can be: a key that is not there when
 * the game reads it, and two keys sharing the same patch of glass so that the
 * wrong one answers. Both are invisible in a screenshot until the moment they
 * cost somebody a life.</p>
 */
public final class KeypadPlanTest extends Test {

    private static final int WIDTH = 480;
    private static final int KEY = 66;

    @Override
    public String name() {
        return "Ba kiểu bàn phím";
    }

    @Override
    public void run() throws Exception {
        whichKeys();
        whereTheSoftKeysAre();
        nothingOverlaps();
        nothingEscapes();
        draggingSurvivesAChangeOfStyle();
        leaningOnTheStick();
        sideways();
        oldProfilesKeepTheirKeypad();
    }

    /** Each style is exactly the keys it promises, and no others. */
    private void whichKeys() {
        KeypadPlan arrows = KeypadPlan.portrait(KeypadPlan.STYLE_ARROWS, WIDTH, KEY, null);
        eq(11, arrows.keys().size(), "arrows: eight ways, fire and two softkeys");
        check(!hasAnyDigit(arrows), "arrows: not one number, which is the point of it");
        check(arrows.has("upLeft") && arrows.has("downRight"), "arrows: the corners are there");

        KeypadPlan full = KeypadPlan.portrait(KeypadPlan.STYLE_FULL, WIDTH, KEY, null);
        for (int digit = 0; digit <= 9; digit++) {
            check(full.has("num" + digit), "full: the keypad carries " + digit);
        }
        check(full.has("star") && full.has("hash"), "full: star and hash too");
        eq(23, full.keys().size(), "full: twelve numbers, nine pad keys and two softkeys");

        KeypadPlan game = KeypadPlan.portrait(KeypadPlan.STYLE_GAME, WIDTH, KEY, null);
        check(game.has("num1") && game.has("num3") && game.has("num7") && game.has("num9"),
                "game: the four digits a game reads");
        check(!game.has("num5") && !game.has("num0") && !game.has("star"),
                "game: and none of the ones it does not");
        check(game.has("stick"), "game: one round pad that steers");
        check(!game.has("up") && !game.has("left")
                        && !game.has("upLeft") && !game.has("downRight"),
                "game: and so no direction keys of its own");
        eq(KeypadPlan.KIND_STICK, game.find("stick").kind(), "game: the pad is a stick");
        eq(8, game.keys().size(), "game: a stick, fire, four digits and two softkeys");

        for (int i = 0; i < game.keys().size(); i++) {
            KeypadPlan.Key key = game.keys().get(i);
            if (key.kind() == KeypadPlan.KIND_SOFT) {
                // The two softkeys carry a word the game wrote, so they stay
                // the wide bar that a word fits on.
                continue;
            }
            check(key.round(), "game: every key of the pad is round — " + key.button());
        }
        KeypadPlan.Key one = full.find("num1");
        check(!one.round(), "full: keys take the shape the player chose");
    }

    /**
     * Where J2ME Loader puts the two softkeys, which is not in a bar.
     *
     * <p>Its arrows layout snaps them west and east of the pad's top corners
     * and its numbers-and-arrows layout snaps them north of them. Getting this
     * wrong does not look wrong — it puts the two keys a game labels somewhere
     * the thumb does not go.</p>
     */
    private void whereTheSoftKeysAre() {
        for (int style = KeypadPlan.STYLE_ARROWS; style <= KeypadPlan.STYLE_GAME; style++) {
            KeypadPlan plan = KeypadPlan.portrait(style, WIDTH, KEY, null);
            check(plan.has("softLeft") && plan.has("softRight"),
                    "style " + style + ": both softkeys are keys of the keypad");
        }

        KeypadPlan arrows = KeypadPlan.portrait(KeypadPlan.STYLE_ARROWS, WIDTH, KEY, null);
        KeypadPlan.Key left = arrows.find("softLeft");
        KeypadPlan.Key right = arrows.find("softRight");
        KeypadPlan.Key upLeft = arrows.find("upLeft");
        KeypadPlan.Key upRight = arrows.find("upRight");
        check(left.right() <= upLeft.x(), "arrows: the left softkey is west of the pad");
        check(right.x() >= upRight.right(), "arrows: the right softkey is east of it");
        check(left.y() < upLeft.bottom() && left.bottom() > upLeft.y(),
                "arrows: and level with the top row rather than above it");

        KeypadPlan full = KeypadPlan.portrait(KeypadPlan.STYLE_FULL, WIDTH, KEY, null);
        check(full.find("softLeft").bottom() <= full.find("up").y(),
                "full: the softkeys sit above the pad");

        // The gamepad puts them across the top, over everything else.
        KeypadPlan game = KeypadPlan.portrait(KeypadPlan.STYLE_GAME, WIDTH, KEY, null);
        check(game.find("softLeft").bottom() <= game.find("stick").y(),
                "game: the softkeys sit above the stick");
        check(game.find("softRight").bottom() <= game.find("fire").y(),
                "game: and above the fire key");
        check(game.find("stick").right() < game.find("fire").x(),
                "game: the stick is under one thumb and fire under the other");

        // Balanced: the same width, the same gap outside each, and the same
        // distance in from the middle. A pair that is not is a pair that
        // looks like a mistake.
        KeypadPlan.Key gameLeft = game.find("softLeft");
        KeypadPlan.Key gameRight = game.find("softRight");
        eq(gameLeft.width(), gameRight.width(), "game: the two softkeys are the same size");
        eq(gameLeft.x(), WIDTH - gameRight.right(),
                "game: and the same distance in from either edge");
        // Centred in its own half, not shoved out to the margin: a pair
        // pushed to the two edges reads as two keys that got away from each
        // other rather than as a pair.
        eq(WIDTH / 4, gameLeft.x() + gameLeft.width() / 2,
                "game: the left softkey is centred in the left half");
        eq(WIDTH * 3 / 4, gameRight.x() + gameRight.width() / 2,
                "game: and the right one in the right half");

        String[] both = {"full", "game"};
        KeypadPlan[] plans = {full, game};
        for (int i = 0; i < plans.length; i++) {
            check(plans[i].find("softLeft").x() < plans[i].find("softRight").x(),
                    both[i] + ": left on the left, right on the right");
        }
    }

    /**
     * No two keys share a pixel.
     *
     * <p>The one failure of a keypad that cannot be seen and cannot be argued
     * with: the player presses where they aimed and something else happens.</p>
     */
    private void nothingOverlaps() {
        // Narrow screens and a zero from a front end still measuring itself:
        // both are real, and both used to push keys on top of one another.
        int[] widths = {WIDTH, 320, 240, 0};
        int[] keys = {KEY, 44, 30, 0};
        for (int style = KeypadPlan.STYLE_ARROWS; style <= KeypadPlan.STYLE_GAME; style++) {
            for (int i = 0; i < widths.length; i++) {
                KeypadPlan plan = KeypadPlan.portrait(style, widths[i], keys[i], null);
                String clash = firstOverlap(plan);
                check(clash == null, "style " + style + " at " + widths[i]
                        + ": no two keys overlap — " + clash);
            }
        }
    }

    /** Every key is inside the strip it was measured for. */
    private void nothingEscapes() {
        for (int style = KeypadPlan.STYLE_ARROWS; style <= KeypadPlan.STYLE_GAME; style++) {
            KeypadPlan plan = KeypadPlan.portrait(style, WIDTH, KEY, null);
            String escaped = null;
            checkFits(style, 0, 0);
            checkFits(style, 240, 30);
            for (int i = 0; i < plan.keys().size(); i++) {
                KeypadPlan.Key key = plan.keys().get(i);
                if (key.x() < 0 || key.right() > WIDTH
                        || key.y() < 0 || key.bottom() > plan.height()) {
                    escaped = key.button();
                }
            }
            check(escaped == null, "style " + style + ": every key is on the screen — " + escaped);
            check(plan.height() > 0, "style " + style + ": the strip has a height");
        }
    }

    /**
     * A keypad asked for in a space too small still lays itself out sanely:
     * it widens to what it needs rather than folding in on itself.
     */
    private void checkFits(int style, int width, int key) {
        KeypadPlan plan = KeypadPlan.portrait(style, width, key, null);
        int leftmost = Integer.MAX_VALUE;
        for (int i = 0; i < plan.keys().size(); i++) {
            leftmost = Math.min(leftmost, plan.keys().get(i).x());
        }
        check(leftmost >= 0, "style " + style + " at " + width
                + ": no key is pushed off the left edge");
    }

    /**
     * A key dragged under the thumb stays under the thumb when the keypad
     * changes, which is what an offset from the standard layout is for.
     */
    private void draggingSurvivesAChangeOfStyle() {
        KeypadArrangement moved = new KeypadArrangement();
        moved.move("fire", 0.5f, 0.25f);

        KeypadPlan plain = KeypadPlan.portrait(KeypadPlan.STYLE_FULL, WIDTH, KEY, null);
        KeypadPlan dragged = KeypadPlan.portrait(KeypadPlan.STYLE_FULL, WIDTH, KEY, moved);
        eq(plain.find("fire").x() + KEY / 2, dragged.find("fire").x(),
                "half a key to the right is half a key to the right");
        eq(plain.find("up").x(), dragged.find("up").x(), "and nothing else moved with it");

        KeypadPlan other = KeypadPlan.portrait(KeypadPlan.STYLE_GAME, WIDTH, KEY, moved);
        KeypadPlan otherPlain = KeypadPlan.portrait(KeypadPlan.STYLE_GAME, WIDTH, KEY, null);
        int gameKey = otherPlain.find("num1").width();
        eq(otherPlain.find("fire").x() + gameKey / 2, other.find("fire").x(),
                "the same drag still means the same thing on another keypad");
    }

    /**
     * Which way the stick is being leaned, which is the whole of how the
     * gamepad steers.
     */
    private void leaningOnTheStick() {
        eq(0, KeypadPlan.stickDirections(0, 0, 100).size(),
                "a thumb resting in the middle is not steering");
        eq(0, KeypadPlan.stickDirections(20, 0, 100).size(),
                "nor is one that has barely moved — the middle is a rest");

        eq("[up]", KeypadPlan.stickDirections(0, -80, 100).toString(), "straight up is up");
        eq("[down]", KeypadPlan.stickDirections(0, 80, 100).toString(), "and down is down");
        eq("[left]", KeypadPlan.stickDirections(-80, 0, 100).toString(), "left is left");
        eq("[right]", KeypadPlan.stickDirections(80, 0, 100).toString(), "right is right");

        // The corner a handset reached with two keys held at once, reached by
        // leaning into it — which is the reason the pad became a stick.
        eq("[up, right]", KeypadPlan.stickDirections(60, -60, 100).toString(),
                "leaning into a corner holds both directions");
        eq("[down, left]", KeypadPlan.stickDirections(-60, 60, 100).toString(),
                "and so does the opposite corner");

        // Just off an axis is still that axis: a thumb is not a protractor.
        eq("[up]", KeypadPlan.stickDirections(20, -90, 100).toString(),
                "a lean a few degrees off straight up is still up");
    }

    /** Turned sideways, each thumb gets a column and its own softkey. */
    private void sideways() {
        KeypadPlan left = KeypadPlan.column(KeypadPlan.STYLE_FULL, true, 220, 520, KEY, null);
        KeypadPlan right = KeypadPlan.column(KeypadPlan.STYLE_FULL, false, 220, 520, KEY, null);
        check(left.has("fire") && left.has("up"), "sideways: the pad is under one thumb");
        KeypadPlan stick = KeypadPlan.column(KeypadPlan.STYLE_GAME, true, 220, 520, KEY, null);
        check(stick.has("stick"), "sideways: the gamepad keeps its stick");
        check(right.has("num5"), "sideways: the numbers under the other");
        check(left.has("softLeft") && !left.has("softRight"),
                "sideways: one softkey per column, in the corner that thumb rests in");
        check(right.has("softRight") && !right.has("softLeft"), "sideways: and the other opposite");
        check(firstOverlap(left) == null, "sideways: the pad column does not overlap itself");
        check(firstOverlap(right) == null, "sideways: nor the numbers column");

        KeypadPlan bare = KeypadPlan.column(KeypadPlan.STYLE_ARROWS, false, 220, 520, KEY, null);
        eq(1, bare.keys().size(), "sideways: an arrows keypad has no numbers column to fill");
    }

    /**
     * A profile written when the numbering meant something else.
     *
     * <p>Before there were three keypads the same field counted full, arrows,
     * numbers and hidden. Read straight, every installed game would come back
     * with a different keypad than the player left it on.</p>
     */
    private void oldProfilesKeepTheirKeypad() {
        eq(GameProfile.KEYPAD_FULL, read(old(0)).keypadLayout(), "old \"full\" is the full keypad");
        check(!read(old(0)).keypadHidden(), "and is not hidden");
        eq(GameProfile.KEYPAD_ARROWS, read(old(1)).keypadLayout(), "old \"arrows only\" is the arrows keypad");
        eq(GameProfile.KEYPAD_FULL, read(old(2)).keypadLayout(),
                "old \"numbers only\" keeps its numbers");

        GameProfile hidden = read(old(3));
        check(hidden.keypadHidden(), "old \"hidden\" is still put away");
        eq(GameProfile.KEYPAD_FULL, hidden.keypadLayout(),
                "and comes back to a keypad rather than to nothing");

        // Written by this version, read straight: hiding is its own answer now.
        com.mobicore.core.model.DeviceProfile device =
                com.mobicore.core.model.DeviceProfile.QVGA_240x320;
        GameProfile now = new GameProfile("s", device,
                com.mobicore.core.model.InputProfile.forKeypad(device.keypad()));
        now.setKeypadLayout(GameProfile.KEYPAD_GAME);
        now.setKeypadHidden(true);
        GameProfile again = read(Json.write(now.toJson()));
        eq(GameProfile.KEYPAD_GAME, again.keypadLayout(), "the chosen keypad survives a save");
        check(again.keypadHidden(), "and so does having put it away");
        eq("Chơi game", again.keypadLayoutName(), "the menu has a name for it");
    }

    // ------------------------------------------------------------- plumbing

    private static String old(int layout) {
        return "{\"suiteId\":\"s\",\"keypadLayout\":" + layout + "}";
    }

    private static GameProfile read(String text) {
        Map<String, Object> json = Json.readObject(text);
        return GameProfile.fromJson(json);
    }

    private static boolean hasAnyDigit(KeypadPlan plan) {
        for (int i = 0; i < plan.keys().size(); i++) {
            if (plan.keys().get(i).kind() == KeypadPlan.KIND_NUMBER) {
                return true;
            }
        }
        return false;
    }

    private static String firstOverlap(KeypadPlan plan) {
        for (int i = 0; i < plan.keys().size(); i++) {
            for (int j = i + 1; j < plan.keys().size(); j++) {
                KeypadPlan.Key a = plan.keys().get(i);
                KeypadPlan.Key b = plan.keys().get(j);
                if (a.x() < b.right() && b.x() < a.right()
                        && a.y() < b.bottom() && b.y() < a.bottom()) {
                    return a.button() + " over " + b.button();
                }
            }
        }
        return null;
    }
}
