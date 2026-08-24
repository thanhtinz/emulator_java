# The emulator core reflects on nothing, but the MIDP classes it registers are
# looked up by name from bytecode, so keep the whole core intact.
-keep class com.mobicore.core.** { *; }
