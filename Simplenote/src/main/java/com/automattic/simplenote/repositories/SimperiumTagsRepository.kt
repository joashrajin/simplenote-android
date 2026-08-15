package com.automattic.simplenote.repositories

import android.util.Log
import com.automattic.simplenote.Simplenote
import com.automattic.simplenote.di.IoDispatcher
import com.automattic.simplenote.models.Note
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.search.SearchQueryBuilder
import com.automattic.simplenote.utils.AppLog
import com.automattic.simplenote.utils.TagUtils
import com.simperium.client.Bucket
import com.simperium.client.BucketObjectNameInvalid
import com.simperium.client.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SimperiumTagsRepository @Inject constructor(
    private val tagsBucket: Bucket<Tag>,
    private val notesBucket: Bucket<Note>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TagsRepository {
    override fun saveTag(tagName: String): Boolean {
        return try {
            TagUtils.createTagIfMissing(tagsBucket, tagName)
            true
        } catch (bucketObjectNameInvalid: BucketObjectNameInvalid) {
            false
        }
    }

    override fun isTagValid(tagName: String): Boolean {
        return TagUtils.hashTagValid(tagName)
    }

    override fun isTagMissing(tagName: String): Boolean {
        return TagUtils.isTagMissing(tagsBucket, tagName)
    }

    override fun isTagConflict(tagName: String, oldTagName: String): Boolean {
        val isRenamingToLexical = TagUtils.hashTag(tagName).equals(TagUtils.hashTag(oldTagName))
        return !isRenamingToLexical && !isTagMissing(tagName)
    }

    override fun getCanonicalTagName(tagName: String): String {
        return TagUtils.getCanonicalFromLexical(tagsBucket, tagName)
    }

    override fun renameTag(tagName: String, oldTag: Tag): Boolean {
        return try {
            val index = if (oldTag.hasIndex()) oldTag.index else tagsBucket.count()
            oldTag.renameTo(oldTag.name, tagName, index, notesBucket)
            true
        } catch (e: BucketObjectNameInvalid) {
            Log.e(Simplenote.TAG, "Unable to rename tag", e)
            false
        }
    }

    override suspend fun deleteTag(tag: Tag) = withContext(ioDispatcher) {
        deleteTagFromNotes(tag)
        tag.delete()
    }

    private fun deleteTagFromNotes(tag: Tag) {
        tag.findNotes(notesBucket, tag.name).use { cursor ->
            while (cursor.moveToNext()) {
                val note = cursor.getObject()
                note.removeTag(tag.name)
            }
        }
    }

    override suspend fun tagsChanged(): Flow<Boolean> = callbackFlow {
        val callbackOnSaveObject = Bucket.OnSaveObjectListener<Tag> { _, _ -> trySend(true).isSuccess }
        val callbackOnDeleteObject = Bucket.OnDeleteObjectListener<Tag> { _, _ -> trySend(true).isSuccess }
        val callbackOnNetworkChange = Bucket.OnNetworkChangeListener<Tag> { _, _, _ -> trySend(true).isSuccess }

        tagsBucket.addOnSaveObjectListener(callbackOnSaveObject)
        tagsBucket.addOnDeleteObjectListener(callbackOnDeleteObject)
        tagsBucket.addOnNetworkChangeListener(callbackOnNetworkChange)
        AppLog.add(AppLog.Type.SYNC, "Added tag bucket listener (TagsActivity)")

        awaitClose {
            tagsBucket.removeOnSaveObjectListener(callbackOnSaveObject)
            tagsBucket.removeOnDeleteObjectListener(callbackOnDeleteObject)
            tagsBucket.removeOnNetworkChangeListener(callbackOnNetworkChange)
            AppLog.add(AppLog.Type.SYNC, "Removed tag bucket listener (TagsActivity)")
        }
    }.flowOn(ioDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun navigationTags(sortAlphabetically: Boolean): Flow<List<Tag>> = callbackFlow {
        val callbackOnSaveObject = Bucket.OnSaveObjectListener<Tag> { _, _ -> trySend(Unit).isSuccess }
        val callbackOnDeleteObject = Bucket.OnDeleteObjectListener<Tag> { _, _ -> trySend(Unit).isSuccess }
        val callbackOnNetworkChange = Bucket.OnNetworkChangeListener<Tag> { _, _, _ -> trySend(Unit).isSuccess }

        tagsBucket.addOnSaveObjectListener(callbackOnSaveObject)
        tagsBucket.addOnDeleteObjectListener(callbackOnDeleteObject)
        tagsBucket.addOnNetworkChangeListener(callbackOnNetworkChange)
        AppLog.add(AppLog.Type.SYNC, "Added tag bucket listeners (NotesActivity)")
        trySend(Unit)

        awaitClose {
            tagsBucket.removeOnSaveObjectListener(callbackOnSaveObject)
            tagsBucket.removeOnDeleteObjectListener(callbackOnDeleteObject)
            tagsBucket.removeOnNetworkChangeListener(callbackOnNetworkChange)
            AppLog.add(AppLog.Type.SYNC, "Removed tag bucket listeners (NotesActivity)")
        }
    }.conflate().mapLatest {
        withContext(ioDispatcher) {
            val query = if (sortAlphabetically) {
                Tag.allSortedAlphabetically(tagsBucket)
            } else {
                Tag.allWithName(tagsBucket)
            }
            query.execute().use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.`object`)
                    }
                }
            }
        }
    }

    override suspend fun allTags(): List<TagItem> = withContext(ioDispatcher) {
        val tagQuery = Tag.all(tagsBucket).reorder().orderByKey().include(Tag.NOTE_COUNT_INDEX_NAME)
        tagQuery.execute().use { cursor -> cursorToTagItems(cursor) }
    }

    override suspend fun searchTags(query: String): List<TagItem> = withContext(ioDispatcher) {
        val tags = Tag.all(tagsBucket)
                .where(Tag.NAME_PROPERTY, Query.ComparisonType.LIKE, "%$query%")
                .orderByKey().include(Tag.NOTE_COUNT_INDEX_NAME)
                .reorder()
        tags.execute().use { cursor -> cursorToTagItems(cursor) }
    }

    override suspend fun suggestTags(query: String): List<String> = withContext(ioDispatcher) {
        val tags = Tag.all(tagsBucket).reorder().order(Tag.NOTE_COUNT_INDEX_NAME, Query.SortType.DESCENDING)

        if (!query.endsWith(SearchQueryBuilder.TAG_PREFIX)) {
            tags.where(Tag.NAME_PROPERTY, Query.ComparisonType.LIKE, "%$query%")
        }

        tags.execute().use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) {
                names.add(cursor.getObject().name)
            }
            names
        }
    }

    private fun cursorToTagItems(cursor: Bucket.ObjectCursor<Tag>): List<TagItem> {
        return (1 .. cursor.count).map {
            cursor.moveToNext()
            val tag = cursor.`object`
            val noteCount: Int = notesBucket
                    .query()
                    .where(Note.TAGS_PROPERTY, Query.ComparisonType.EQUAL_TO, tag.name)
                    .count()
            TagItem(tag, noteCount)
        }
    }
}
