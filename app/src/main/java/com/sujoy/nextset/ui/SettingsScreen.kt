package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.data.*

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onTheme: (ThemeMode) -> Unit,
    onRest: (Int) -> Unit,
    notifications: @Composable () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { PageTitle("On your terms", "Settings") }
        item {
            Text(
                stringResource(R.string.ui_appearance),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            ThemeMode.entries.forEach { mode ->
                Surface(
                    onClick = { onTheme(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.theme == mode,
                            onClick = null,
                            modifier = Modifier.padding(16.dp),
                        )
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Use device theme"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            Modifier.padding(vertical = 18.dp),
                        )
                    }
                }
            }
        }
        item { notifications() }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.ui_a_useful_starting_point),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    R.string.ui_default_rest_applies_to_new_exercise_blocks_existing_workouts_kee
                ),
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RestSetting("Default rest", settings.defaultRest, onRest)
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.ui_private_by_design),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(
                    R.string.ui_nextset_works_entirely_offline_there_are_no_accounts_ads_analytic
                ),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                stringResource(
                    R.string.ui_uninstalling_the_app_or_clearing_its_storage_removes_your_data_ex
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.ui_nextset_1_0),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
