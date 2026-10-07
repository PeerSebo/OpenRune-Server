package org.rsmod.api.farming.persistence

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.farming.FarmingBins
import org.rsmod.api.farming.FarmingPatches

public class FarmingCharacterData(
    public val patches: FarmingPatches?,
    public val bins: FarmingBins? = null,
) : CharacterDataStage.Segment
