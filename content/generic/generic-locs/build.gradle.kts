plugins {
    id("base-conventions")
}

dependencies {
    testImplementation(projects.api.registry)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.or2.all.cache)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
