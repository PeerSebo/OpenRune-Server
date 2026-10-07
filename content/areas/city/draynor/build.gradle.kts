plugins {
    id("base-conventions")
}

dependencies {
    testImplementation(projects.content.generic.genericNpcs)
    testImplementation(projects.content.generic.genericLocs)
    implementation(projects.api.invtx)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.quest)
    implementation(projects.content.interfaces.omnishop)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
