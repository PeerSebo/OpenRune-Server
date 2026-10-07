plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.net)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
