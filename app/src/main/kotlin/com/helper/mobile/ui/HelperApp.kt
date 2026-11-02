package com.helper.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.helper.mobile.ui.theme.HelperTheme

/** Punto de entrada de la UI. Las pantallas de login, repartidor y mecánico se suman en los próximos PR. */
@Composable
fun HelperApp() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("helper", style = MaterialTheme.typography.headlineLarge)
            Text("Un mecánico hasta donde te quedaste", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelperAppPreview() {
    HelperTheme { HelperApp() }
}
