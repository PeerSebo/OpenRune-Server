plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.combat.combatCommons)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.coroutines.core)
    implementation(projects.api.market)
    implementation(projects.server.services)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
