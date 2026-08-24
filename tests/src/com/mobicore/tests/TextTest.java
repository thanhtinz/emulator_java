package com.mobicore.tests;

import com.mobicore.core.util.Text;

public final class TextTest extends Test {

    @Override
    public String name() {
        return "Text helpers";
    }

    @Override
    public void run() {
        eq(3, Text.split("a,b,c", ',').length, "split counts parts");
        eq("b", Text.split("a,b,c", ',')[1], "split keeps order");
        eq(1, Text.split("solo", ',').length, "split without separator");
        eq("", Text.split("a,", ',')[1], "split keeps trailing empty part");
        eq("bounce-tales", Text.slug("Bounce Tales"), "slug lowercases and dashes");
        eq("nokia-corp", Text.slug("  Nokia  Corp. "), "slug trims punctuation");
        eq("untitled", Text.slug("!!!"), "slug falls back for empty result");
        eq("1-0", Text.slug("1.0"), "slug keeps digits");
        check(Text.isEmpty(null), "null is empty");
        check(Text.isEmpty("   ") == false, "blank is not reported empty by isEmpty");
        eq(null, Text.trimOrNull("   "), "trimOrNull collapses blank to null");
        eq("fallback", Text.orDefault(null, "fallback"), "orDefault uses fallback");
    }
}
