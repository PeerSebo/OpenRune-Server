package org.rsmod.content.other.pets.dogs

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class DogsTableTest {
    @Test
    fun `each puppy has an adult form and a unique follower item`() {
        val forms = Dogs.all
        assertEquals(36, forms.count { it.puppy })
        assertEquals(36, forms.count { !it.puppy })
        assertEquals(forms.size, forms.map { it.form.objId }.toSet().size)
        for (puppy in forms.filter { it.puppy }) {
            val adult = Dogs.adult(puppy)
            assertEquals(puppy.breed, adult.breed)
            assertEquals(puppy.colour, adult.colour)
            assertNotNull(Dogs.formForObj(puppy.form.objId))
            assertNotNull(Dogs.formForObj(adult.form.objId))
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
