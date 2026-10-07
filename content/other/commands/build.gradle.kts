plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(libs.simmetrics.core)
    implementation(projects.api.areaChecker)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.instances)
    implementation(projects.api.registry)
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.realmConfig)
    implementation(projects.api.spellsAutocast)
    implementation(projects.content.quest)

    implementation(projects.api.utils.utilsSystem)
    implementation(projects.engine.utilsBits)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.net)
    testImplementation(projects.content.interfaces.makeover)
    testImplementation(projects.content.other.cheatMenu)
    testImplementation(projects.content.other.commandBrowser)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
