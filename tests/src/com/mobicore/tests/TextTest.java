package com.mobicore.tests;

import com.mobicore.core.util.Text;

public final class TextTest extends Test {

    @Override
    public String name() {
        return "Text helpers";
    }

    @Override
    public void run() {
        // Search keys: what a Vietnamese name looks like once the marks are
        // taken off, which is how it gets typed on a phone.
        eq("nguoi chay tren may", Text.searchKey("Người Chạy Trên Mây"),
                "every Vietnamese mark comes off");
        eq("duong dua", Text.searchKey("Đường Đua"), "including the crossed D");
        eq("ao anh", Text.searchKey("ẢO ẢNH"), "and capitals are lowered");
        eq("sky runner 2", Text.searchKey("Sky Runner 2"),
                "a name with no marks is left as it is");
        eq("", Text.searchKey(null), "and nothing is not a crash");

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
