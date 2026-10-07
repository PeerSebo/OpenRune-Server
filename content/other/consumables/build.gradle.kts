plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.attr)
    implementation(projects.api.death)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.specials)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
