package com.ishaara.mobile.skills

import android.service.notification.NotificationListenerService

/**
 * Holding Notification access lets the app read media sessions and notifications.
 * The system binds this while access is on.
 */
class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    companion object {
        @Volatile
        var instance: MediaAccessService? = null
            private set
    }
}
