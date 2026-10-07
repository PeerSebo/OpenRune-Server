plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invStorage)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
