plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.interfaces.trading)
    testImplementation(libs.fastutil)
    implementation(projects.api.areaChecker)
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.death)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.shops)
    implementation(projects.content.interfaces.bank)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.invStorage)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
