package com.appmcpe.hub

import android.app.Application
import com.appmcpe.hub.install.InstallManager
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.user.UserLists

class AddonHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        InstallManager.init(this)
        ParentalStore.init(this)
        UserLists.init(this)
    }
}
