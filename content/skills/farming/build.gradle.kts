plugins {
    id("base-conventions")
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.farming)
    implementation(projects.content.interfaces.skillGuides)
    testImplementation(projects.api.attr)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.net)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.jackson.module.kotlin)
}
