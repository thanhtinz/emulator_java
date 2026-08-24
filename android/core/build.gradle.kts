plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    named("main") {
        // Points at the shared, platform-independent emulator core. Keeping a
        // single copy is what lets Android and iOS run identical bytecode.
        java.setSrcDirs(listOf(rootProject.file("../core/src")))
    }
}
