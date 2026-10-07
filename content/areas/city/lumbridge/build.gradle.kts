plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.account)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.quest)
    implementation(projects.content.other.achievementDiaries)
    implementation(projects.content.interfaces.journalTab)
    implementation(projects.api.attr)
    implementation(projects.api.music)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.fastutil)
}

tasks.test {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
