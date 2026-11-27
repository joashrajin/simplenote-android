package com.automattic.simplenote.models;

import android.util.Log;

import com.automattic.simplenote.utils.TagUtils;
import com.simperium.client.Bucket;
import com.simperium.client.BucketObjectMissingException;
import com.simperium.client.BucketObjectNameInvalid;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to the notes bucket and creates tags for any non-existent tags in the tags bucket.
 */
public class NoteTagger implements Bucket.Listener<Note> {
    private final Bucket<Tag> mTagsBucket;
    private final Map<String, Set<String>> mTagsBeforeUpdate = new ConcurrentHashMap<>();

    public NoteTagger(Bucket<Tag> tagsBucket) {
        mTagsBucket = tagsBucket;
    }

    /*
    * When a note is saved check its array of tags to make sure there is a corresponding tag
    * object and create one if necessary. Re-save all tags so their indexes are updated.
    * */
    @Override
    public void onSaveObject(Bucket<Note> bucket, Note note) {
        // make sure we have tags
        List<String> tags = note.getTags();

        for (String name : tags) {
            try {
                TagUtils.createTagIfMissing(mTagsBucket, name);
            } catch (BucketObjectNameInvalid e) {
                Log.e("Simplenote.NoteTagger", "Invalid tag name " + "\"" + name + "\"", e);
            }
        }
    }

    @Override
    public void onDeleteObject(Bucket<Note> noteBucket, Note note) {
    }

    @Override
    public void onNetworkChange(Bucket<Note> notesBucket, Bucket.ChangeType changeType, String key) {
        if (changeType == Bucket.ChangeType.RESET) {
            mTagsBeforeUpdate.clear();
            return;
        }

        if (key == null) {
            return;
        }

        Set<String> previousTags = mTagsBeforeUpdate.remove(key);

        if (changeType == Bucket.ChangeType.INSERT) {
            Set<String> currentTags = getStoredTags(notesBucket, key);
            if (currentTags != null && !currentTags.isEmpty()) {
                refreshTagObservers();
            }
        } else if (changeType == Bucket.ChangeType.MODIFY && previousTags != null) {
            Set<String> currentTags = getStoredTags(notesBucket, key);
            if (currentTags != null && !previousTags.equals(currentTags)) {
                refreshTagObservers();
            }
        }
    }

    @Override
    public void onBeforeUpdateObject(Bucket<Note> bucket, Note object) {
        String key = object.getSimperiumKey();
        if (key != null) {
            mTagsBeforeUpdate.put(key, getTags(object));
        }
    }

    @Override
    public void onLocalQueueChange(Bucket<Note> bucket, Set<String> queuedObjects) {

    }

    @Override
    public void onSyncObject(Bucket<Note> bucket, String key) {

    }

    private Set<String> getStoredTags(Bucket<Note> notesBucket, String key) {
        try {
            return getTags(notesBucket.getObject(key));
        } catch (BucketObjectMissingException exception) {
            return null;
        }
    }

    private Set<String> getTags(Note note) {
        Set<String> tags = new HashSet<>();
        Object storedTags = note.getProperty(Note.TAGS_PROPERTY);
        if (!(storedTags instanceof JSONArray)) {
            return tags;
        }

        JSONArray tagsArray = (JSONArray) storedTags;
        for (int index = 0; index < tagsArray.length(); index++) {
            String tag = tagsArray.optString(index);
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private void refreshTagObservers() {
        mTagsBucket.notifyOnNetworkChangeListeners(Bucket.ChangeType.INDEX);
    }
}
