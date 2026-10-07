plugins {
    id("base-conventions")
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.kotlin.coroutines.core)

    implementation(rootProject.libs.or2.all.cache)
    implementation(rootProject.project.libs.or2.tools)
    implementation(rootProject.project.libs.or2.server.utils)
    api(libs.or2.definition)
    api(libs.or2.filestore)
    api(libs.or2.filesystem)

    findContentPlugins().forEach { runtimeOnly(it) }
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.6")
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("me.tongfei:progressbar:0.9.2")
    implementation("io.netty:netty-buffer:4.1.107.Final")
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.databind)
    implementation("dev.or2:toml-rsconfig:1.0")
    compileOnly(libs.logback.classic)
    implementation(libs.fastutil)
    implementation(libs.classgraph)
    testImplementation("me.filby:clientscript-compiler:0.0.6-openrune")
}

// Pack-only dependencies keep game scripts and their dependencies off the cache-build classpath.
fun findContentPlugins(): List<Project> =
    project(":content").subprojects.filter { it.name.endsWith("-pack") && it.buildFile.exists() }

tasks {
    test {
        val shopCatalog = rootProject.file(".data/raw-cache/server/shops")
        systemProperty("shop.catalogDir", shopCatalog.absolutePath)
        inputs.dir(shopCatalog)
        // Neptune 0.0.6 constructs a singleton TupleType in IfParamCommandHandler, contrary to its
        // own Kotlin assert. Match the cache packer's assertion mode for these compiler tests.
        // Kotlin routes assert through this shared class; JVM assertions and JUnit checks stay on.
        jvmArgs("-da:kotlin._Assertions")
    }

    register("buildCache",JavaExec::class) {
        group = "cache"
        description = "Build Cache"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args = listOf("BUILD")
    }

    register<JavaExec>("dumpLocs") {
        group = "cache"
        description = "Prints native object placements: -Plocs=loc.name,12345 or -Plocs=x1:z1:x2:z2"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args("DUMP_LOCS", providers.gradleProperty("locs").getOrElse(""))
    }

    register<JavaExec>("dumpSeqs") {
        group = "cache"
        description = "Prints animation timing: -Pseqs=seq.name,12345"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args("DUMP_SEQS", providers.gradleProperty("seqs").getOrElse(""))
    }

    register("freshCache",JavaExec::class) {
        group = "cache"
        description = "Fresh Install Cache"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args = listOf("FRESH_INSTALL")
    }

    register<JavaExec>("dumpPohTemplate") {
        group = "cache"
        description = "Dumps POH construction template map squares (research only)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.PohTemplateDump")
        workingDir = rootProject.projectDir
        (project.findProperty("poh.square") as String?)?.let {
            systemProperty("pohSquare", it)
        }
        dependsOn("classes")
    }

    register<JavaExec>("queryMapLocs") {
        group = "cache"
        description = "Queries map loc placements: -Dnear=x,z[,level] | -Dloc=<symbol> | -Dop=<text>"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.MapLocQuery")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        for (key in listOf("near", "loc", "op", "radius", "limit", "climbables")) {
            System.getProperty(key)?.let { systemProperty(key, it) }
        }
    }

    register<JavaExec>("auditMapPlanes") {
        group = "cache"
        description =
            "Compares loc/tile plane resolution rules against the map archive: -Dtile=x,z | -Daudit"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.MapPlaneAudit")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        for (key in listOf("tile", "radius", "audit", "blocking", "symbols", "zones")) {
            System.getProperty(key)?.let { systemProperty(key, it) }
        }
    }

    register<JavaExec>("examineAudit") {
        group = "cache"
        description = "Counts packed examine text for npcs/locs/items (read-only audit)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.ExamineAudit")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("itemTomlParamAudit") {
        group = "cache"
        description = "Audits what the items.toml param overlay would change (read-only audit)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.ItemTomlParamAudit")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("statReqAudit") {
        group = "cache"
        description = "Audits packed equipment stat requirements (read-only audit)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.StatReqAudit")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("toolStatReqAudit") {
        group = "cache"
        description = "Audits equippable items that carry no wield requirement (read-only audit)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.ToolStatReqAudit")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("toolStatReqTomlCheck") {
        group = "cache"
        description = "Pre-flights the items.toml statreq param overrides without packing (read-only)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.ToolStatReqTomlCheck")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("dumpWorldEntities") {
        group = "cache"
        description = "Decodes the cache worldentity archive - Sailing boat geometry (research only)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.WorldEntityDump")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("dumpSailingDb") {
        group = "cache"
        description = "Dumps dbtable.sailing_boat_hull column types from the SERVER cache (research only)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.SailingDbProbe")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("dumpPohFurnitureMap") {
        group = "cache"
        description = "Resolves POH furniture dbrow -> built loc by model-id join (research only)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.PohFurnitureMap")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register("cleanCs2", JavaExec::class) {
        group = "tools"
        description = "Deletes this checkout's generated CS2 workspace for the configured revision."

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args = listOf("CLEAN_CS2")
    }

    register<JavaExec>("mergePluginGamevals") {
        group = "cache"
        description =
            "Writes plugin gamevals.toml entries into .data/gamevals/*.rscm (used by release CI)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.gamevals.PluginGamevalMergerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

}
