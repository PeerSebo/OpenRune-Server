plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(libs.rsprot.api)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.content.interfaces.collectionLog)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.quest)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.combat.combatWeapon)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.dir(rootProject.file(".data/gamevals"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
