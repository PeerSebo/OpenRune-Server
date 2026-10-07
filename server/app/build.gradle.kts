import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow)
    id("base-conventions")
    application
}

application {
    mainClass.set("org.rsmod.server.app.GameServerKt")
    applicationDefaultJvmArgs = listOf(
        "-XX:AutoBoxCacheMax=65535", "-Xms1g", "-Xmx4g",
        "-Xlog:gc*,safepoint:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10M",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=logs/heap-dumps",
    )
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.clikt)
    implementation(libs.guice)
    implementation(libs.classgraph)
    implementation(libs.rsprot.api)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.openrs2.cache)
    implementation(projects.api.cache)
    implementation(projects.api.core)
    implementation(projects.api.gameProcess)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.invPlugin)
    implementation(projects.api.net)
    implementation(projects.api.objPlugin)
    implementation(projects.api.player)
    implementation(projects.api.parsers.jackson)
    implementation(projects.api.parsers.json)
    implementation(projects.api.parsers.toml)
    implementation(projects.api.registry)
    implementation(projects.api.shops)
    implementation(projects.engine.annotations)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.engine.routefinder)
    implementation(projects.engine.plugin)
    implementation(projects.server.install)
    implementation(projects.server.logging)
    implementation(projects.server.services)
    implementation(projects.server.shared)
}

tasks.named<JavaExec>("run") {
    description = "Runs the RS Mod game server"
    workingDir = rootProject.projectDir
    doFirst { rootProject.file("logs/heap-dumps").mkdirs() }
}

tasks.register<JavaExec>("validatePluginRegistration") {
    group = "verification"
    description = "Validates packed cache, plugin injection and event registration without starting services"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.rsmod.server.app.PluginRegistrationValidationKt")
    workingDir = rootProject.projectDir
    maxHeapSize = "4g"
    systemProperty("logback.configurationFile", "plugin-registration-logback.xml")
    environment("OPENRUNE_CENTRAL_HOST", "")
    environment("OPENRUNE_CENTRAL_PORT", "")
    environment("OPENRUNE_WORLD_KEY", "")
    providers.gradleProperty("npcAudit").orNull?.let { systemProperty("openrune.npcAudit", it) }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("server.jar")
    mergeServiceFiles()
    // The complete game exceeds ZIP's 65535-entry limit; ZIP64 prevents a partial fat jar.
    isZip64 = true
}
