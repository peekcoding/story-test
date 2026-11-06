package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.StoryTestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StoryTestApp : Application() {

    lateinit var repository: StoryTestRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = StoryTestRepository(db)

        CoroutineScope(Dispatchers.IO).launch {
            repository.seedDefaultsIfEmpty()
        }
    }
}
