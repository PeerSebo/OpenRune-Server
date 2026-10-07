plugins {
    id("base-conventions")
}

tasks.test {
    workingDir(rootProject.projectDir)
}

dependencies {
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.instances)
    implementation(projects.api.pluginCommons)
}
