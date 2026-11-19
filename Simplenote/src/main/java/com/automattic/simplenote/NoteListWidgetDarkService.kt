package com.automattic.simplenote

import android.content.Intent
import android.widget.RemoteViewsService

class NoteListWidgetDarkService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteListWidgetFactory(applicationContext, intent)
    }
}
