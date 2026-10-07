plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.smithing)
    implementation(projects.content.skills.utils)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
}
