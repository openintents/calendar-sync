package org.openintents.calendar.sync

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.openintents.calendar.R

/**
 * Activity for holding EntryListFragment.
 */
class EntryListActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_list)
    }
}
