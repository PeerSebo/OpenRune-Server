plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.guice)
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(projects.api.attr)
    implementation(projects.api.player)
    implementation(projects.api.registry)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
