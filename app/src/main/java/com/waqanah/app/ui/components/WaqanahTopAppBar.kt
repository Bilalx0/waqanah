package com.waqanah.app.ui.components

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.waqanah.app.ui.navigation.NavScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaqanahTopAppBar(
    currentRoute: String?,
    navController: NavHostController
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Waqanah (وقانة)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        actions = {
            if (currentRoute != NavScreen.Settings.route) {
                IconButton(onClick = { navController.navigate(NavScreen.Settings.route) }) {
                    Text("⚙️", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    )
}
