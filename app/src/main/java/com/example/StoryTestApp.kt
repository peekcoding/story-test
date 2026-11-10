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

    lateinit var portalRepository: com.example.portal.PortalRepository
        private set

    /** Posts the portal's work, from the button or automatically from PortalService. */
    lateinit var workRunner: com.example.portal.WorkRunner
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = StoryTestRepository(db)
        portalRepository = com.example.portal.PortalRepository(this)
        workRunner = com.example.portal.WorkRunner(repository, portalRepository)

        CoroutineScope(Dispatchers.IO).launch {
            repository.seedDefaultsIfEmpty()
        }

        // Start PortalService if phone is registered
        if (portalRepository.isRegistered()) {
            com.example.portal.PortalService.start(this)
        }
    }
}
