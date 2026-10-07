plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.smithing)
    implementation(projects.content.skills.utils)
    implementation(projects.content.quest)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.interfaces.collectionLog)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.net)
    testImplementation(projects.api.realm)
    testImplementation(projects.api.music)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
