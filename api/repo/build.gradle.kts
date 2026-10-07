plugins {
    id("base-conventions")

}

kotlin {
    explicitApi()
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}

dependencies {
    implementation(libs.guice)
    implementation(libs.fastutil)
    implementation(projects.api.playerOutput)
    implementation(projects.api.registry)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    testImplementation(projects.engine.events)
}
