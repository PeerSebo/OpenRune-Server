package dev.openrune

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object DirectoryConstants {
    val PLUGINS_PATH: Path = Paths.get("plugins")
    val DATA_PATH: Path = Paths.get(".data")
    val CACHE_PATH: Path = DATA_PATH.resolve("cache")
    private val CS2_WORKSPACE_PATH: Path = Paths.get("../.data/cs2").toAbsolutePath().normalize()

    // Generated compiler inputs belong to this checkout and cache revision. A shared AppData
    // directory can silently reuse another checkout's symbols or a different minor revision.
    val CS2_PATH: Path
        get() {
            val (major, minor) = revision
            val version = if (minor >= 0) "$major.$minor" else "$major-default"
            return CS2_WORKSPACE_PATH.resolve(version)
        }

    fun cleanCs2() {
        val dir = CS2_PATH.toFile().canonicalFile
        check(dir.toPath().parent == CS2_WORKSPACE_PATH.toFile().canonicalFile.toPath()) {
            "Refusing to clean CS2 outside the generated project workspace: $dir"
        }

        if (!dir.exists()) {
            println("No CS2 directory at ${dir.absolutePath}")
            return
        }

        if (!dir.deleteRecursively()) {
            error("Failed to delete ${dir.absolutePath}")
        }

        println("Deleted CS2 directory: ${dir.absolutePath}")
    }

    fun projectAppName(): String {
        val file = listOf(
            "game.yml",
            "../game.yml",
            "game.example.yml",
            "../game.example.yml",
        )
            .map(Paths::get)
            .map(Path::toFile)
            .firstOrNull(File::exists)

        val raw = file
            ?.useLines { lines ->
                lines
                    .firstOrNull { it.trimStart().startsWith("name:") }
                    ?.substringAfter("name:")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("'")
                    ?.trim()
            }
            .orEmpty()

        return raw
            .replace(Regex("""[<>:"/\\|?*\p{Cntrl}]"""), "_")
            .trim()
            .trimStart('.')
            .trimEnd('.')
            .ifBlank { "OpenRune" }
    }

    fun userAppDataDir(): Path {
        val home = Paths.get(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()

        return when {
            os.contains("win") -> {
                System.getenv("LOCALAPPDATA")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Paths::get)
                    ?: System.getenv("APPDATA")
                        ?.takeIf(String::isNotBlank)
                        ?.let(Paths::get)
                    ?: home.resolve("AppData/Local")
            }

            os.contains("mac") ->
                home.resolve("Library/Application Support")

            else -> {
                System.getenv("XDG_DATA_HOME")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Paths::get)
                    ?: home.resolve(".local/share")
            }
        }
    }
}
