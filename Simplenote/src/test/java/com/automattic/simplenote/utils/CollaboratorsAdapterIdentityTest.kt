package com.automattic.simplenote.utils

import com.automattic.simplenote.utils.CollaboratorsAdapter.CollaboratorDataItem.CollaboratorItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CollaboratorsAdapterIdentityTest {
    @Test
    fun distinctEmailsWithCollidingHashesHaveDistinctItemIds() {
        val firstEmail = "Aa@example.com"
        val secondEmail = "BB@example.com"

        assertEquals(firstEmail.hashCode(), secondEmail.hashCode())
        assertNotEquals(CollaboratorItem(firstEmail).id, CollaboratorItem(secondEmail).id)
    }
}
