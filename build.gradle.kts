plugins {
    kotlin("multiplatform") version "1.3.50"
}

repositories {
    mavenCentral()
}

kotlin {
    macosX64("pacman") {
        sourceSets["pacmanMain"].kotlin.srcDir("src")
        sourceSets["pacmanTest"].kotlin.srcDir("test")

        binaries {
            executable(buildTypes = setOf(DEBUG)) {
                entryPoint = "main"
            }
        }

        val main by compilations.getting
        val interop by main.cinterops.creating {
            defFile(project.file("ncurses.def"))
            packageName("ncurses")
            includeDirs(
                "/usr/local/Cellar/ncurses/6.1/include/",
                "/usr/local/Cellar/ncurses/6.1/include/ncursesw"
            )
        }
    }
}
