package com.bento.calendar

import android.app.Application
import com.bento.calendar.reminders.ReminderScheduler

class BentoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
    }
}
