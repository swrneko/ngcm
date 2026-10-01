package com.swrneko.glyphmeter

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one architectural rule that is easy to break by accident: the pure layers
 * must stay pure, or they stop being testable without an emulator.
 */
class ModuleBoundariesTest {

    // Gradle runs unit tests with the module directory as the working directory.
    private val projectRoot: File = File(System.getProperty("user.dir")!!).parentFile!!

    private val pureModules = listOf("core/model", "core/layout", "core/animation")

    private val forbiddenImports = listOf(
        "import android.",
        "import androidx.",
        "import com.nothing.ketchum",
    )

    @Test
    fun `the pure modules never import android or the nothing sdk`() {
        val violations = mutableListOf<String>()

        for (module in pureModules) {
            val sources = File(projectRoot, "$module/src")
            // A missing directory must fail loudly: skipping it would turn this test into a
            // check that passes without having looked at anything.
            assertTrue("sources of $module not found at $sources", sources.isDirectory)

            val files = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            assertTrue("no Kotlin files found in $sources", files.isNotEmpty())

            for (file in files) {
                for (line in file.readLines()) {
                    if (forbiddenImports.any { line.trimStart().startsWith(it) }) {
                        violations += "${file.relativeTo(projectRoot)}: ${line.trim()}"
                    }
                }
            }
        }

        assertTrue(
            "pure modules must not depend on Android:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
