plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.instances)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(libs.fastutil)
    testImplementation(projects.api.registry)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
