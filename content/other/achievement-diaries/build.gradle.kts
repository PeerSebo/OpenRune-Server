plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.death)
    implementation(projects.api.invtx)
    implementation(projects.content.quest)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.dir(rootProject.file(".data/gamevals-binary"))
}
