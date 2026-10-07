import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

rootProject.name = "OpenRune-Server"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val centralServerBuild = providers.gradleProperty("openrune.central.includeBuild")
if (centralServerBuild.isPresent) {
    includeBuild(centralServerBuild.get())
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.openrs2.org/repository/openrs2-snapshots")
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    }
}

include(
    "api",
    "content",
    "engine",
    "server",
    "or-cache",
)

includeProjects(project(":api"))
includeProjects(project(":content"))
includeProjects(project(":engine"))
includeProjects(project(":server"))

fun includeProjects(pluginProject: ProjectDescriptor) {
    val projectPath = pluginProject.projectDir.toPath()
    val generatedDirectories = setOf("build", "out", "target", ".gradle", ".git", "node_modules")
    Files.walkFileTree(
        projectPath,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes,
            ): FileVisitResult {
                if (directory != projectPath && directory.fileName.toString() in generatedDirectories) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                searchProject(pluginProject.name, projectPath, directory)
                return FileVisitResult.CONTINUE
            }
        },
    )
}

fun searchProject(parentName: String, root: Path, currentPath: Path) {
    val hasBuildFile = Files.exists(currentPath.resolve("build.gradle.kts"))
    if (!hasBuildFile) {
        return
    }
    val relativePath = root.relativize(currentPath)
    if (relativePath.toString().isEmpty()) {
        return
    }
    val projectName = relativePath.toString().replace(File.separator, ":")
    val projectPath = "$parentName:$projectName"
    include(projectPath)

    // Give plugin packs distinct names; shared org.rsmod:pack coordinates collapse during dependency resolution.
    val project = project(":$projectPath")
    if (project.name == "pack") {
        project.name = "${project.parent?.name}-pack"
    }
}
