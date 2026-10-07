plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.config)
    implementation(projects.api.instances)
    implementation(projects.api.npc)
    implementation(projects.api.script)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.player)
    implementation(projects.api.gameProcess)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.fastutil)
    testImplementation(libs.or2.all.cache)
    testImplementation(projects.api.registry)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/LIVE"))
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
