package com.appmcpe.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import com.appmcpe.hub.install.DownloadService
import com.appmcpe.hub.install.InstallManager
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScreen() }
    }

    override fun onStart() {
        super.onStart()
        InstallManager.appInForeground = true
        // El usuario volvió: el aviso de "descarga lista" ya no hace falta.
        NotificationManagerCompat.from(this).cancel(DownloadService.DONE_ID)
    }

    override fun onStop() {
        InstallManager.appInForeground = false
        // El modo adulto no debe quedar encendido para el siguiente que use el teléfono.
        if (!isChangingConfigurations) ParentalStore.setAdultMode(false)
        super.onStop()
    }
}
