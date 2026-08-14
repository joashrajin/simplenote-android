package com.automattic.simplenote;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.matcher.ViewMatchers.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsNot.not;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NoteListFragmentTest {
    @Rule
    public ActivityTestRule<NotesActivity> mActivityRule = new ActivityTestRule<>(NotesActivity.class);
    private NotesActivity mActivity;

    @Before
    public void setUp() throws Exception {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Test to reproduce issue #142: a non-search refresh delivers a cursor, then a search
     * string is applied, and the NotesCursorAdapter must keep rendering the delivered cursor
     * without reaching for the <code>match_offsets</code> field it never carried. The adapter
     * now keys off the delivered search snapshot, so the live query text cannot desynchronize
     * it from its cursor.
     * <p>
     * See: https://github.com/Simperium/simplenote-android/issues/142
     */
    @Test
    public void testNonSearchCursorReturnsAfterSearchApplied() {
        NoteListFragment noteListFragment = mActivity.getNoteListFragment();

        assertThat(noteListFragment, not(nullValue()));
        noteListFragment.refreshList();

        NoteListFragment.NotesCursorAdapter adapter = noteListFragment.mNotesAdapter;
        mActivity.runOnUiThread(() -> noteListFragment.searchNotes("welcome", false));

        assertThat(adapter.getCount(), is(1));

    }
}
