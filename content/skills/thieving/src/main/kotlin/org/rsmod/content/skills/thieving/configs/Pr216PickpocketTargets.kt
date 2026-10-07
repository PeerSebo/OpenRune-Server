package org.rsmod.content.skills.thieving.configs

internal object Pr216PickpocketTargets {
    val manWoman: List<String>
        get() = ThievingNpcData.additionalNpcs("dbrow.thieving_pickpocket_man_woman")

    val guards: List<String>
        get() = ThievingNpcData.additionalNpcs("dbrow.thieving_pickpocket_guard")

    val hamGuards: List<String>
        get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_ham_guard").npcs
}
