plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.interfaces.collectionLog)
    implementation(libs.rsprot.api)
    implementation(libs.or2.all.cache)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.net)
    testImplementation(projects.content.generic.genericLocs)
    testImplementation(libs.fastutil)
    testImplementation(libs.kotlin.coroutines.core)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/cache/LIVE"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
