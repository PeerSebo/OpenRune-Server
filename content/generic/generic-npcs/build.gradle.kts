plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    testImplementation(libs.fastutil)
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.utils.utilsLogging)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/LIVE"))
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
