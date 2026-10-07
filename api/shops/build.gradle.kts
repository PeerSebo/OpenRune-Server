plugins {
    id("base-conventions")

}

kotlin {
    explicitApi()
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
}

dependencies {
    implementation(libs.kotlin.inline.logger)
    runtimeOnly(libs.logback.classic)
    implementation(libs.fastutil)
    implementation(libs.guice)
    implementation(projects.api.cache)
    implementation(projects.api.attr)
    implementation(projects.api.config)
    implementation(projects.api.gameProcess)
    implementation(projects.api.invtx)
    implementation(projects.api.market)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.script)


    implementation(projects.api.utils.utilsFormat)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.objtx)
    implementation(projects.engine.plugin)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.rsprot.api)
}
