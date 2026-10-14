package com.appmcpe.hub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.appmcpe.hub.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appmcpe.hub.install.McInstaller

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val diagnosis by vm.diagnosis.collectAsState()
    var url by remember { mutableStateOf("") }
    val mcInstalled = remember { McInstaller.isMinecraftInstalled(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::installFromUri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (mcInstalled) stringResource(R.string.tools_mc_found) else stringResource(R.string.tools_mc_missing),
            fontSize = 18.sp,
        )

        BigButton(stringResource(R.string.tools_test_pack), Green) { vm.installTestPack() }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.tools_link_label)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        BigButton(stringResource(R.string.tools_download_install), DarkGreen, enabled = url.isNotBlank()) {
            vm.installFromUrl(url)
        }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(stringResource(R.string.tools_pick_file), fontSize = 18.sp) }

        OutlinedButton(
            onClick = vm::runDiagnosis,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(stringResource(R.string.tools_test_connection), fontSize = 18.sp) }
        diagnosis?.forEach { Text(it, fontSize = 14.sp) }
    }
}

@Composable
fun BigButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(
            text,
            fontSize = 14.sp, // pixelada: más grande no cabe ("INSTALAR TODO (3)") en pantallas de 320 dp
            fontWeight = FontWeight.Bold,
            fontFamily = PixelFont,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
