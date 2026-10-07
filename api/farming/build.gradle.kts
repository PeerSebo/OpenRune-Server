plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.guice)
    implementation(libs.openrune.central.common)
    implementation(projects.api.account)
    implementation(projects.api.attr)
    implementation(projects.api.db)
    implementation(projects.api.gameProcess)
    implementation(projects.api.parsers.json)
    implementation(projects.api.player)
    implementation(projects.api.random)
    implementation(projects.api.registry)
    implementation(projects.api.script)
    implementation(projects.api.utils.utilsMap)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    testImplementation(kotlin("test"))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.module.kotlin)
}
