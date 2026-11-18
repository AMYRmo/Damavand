package com.amyrmo.damavand.lite.ui.shortcut

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.amyrmo.damavand.lite.AppLog
import com.amyrmo.damavand.lite.ProfileRepository
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.SettingsStore
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** One profile the Tasker setting can start or stop, by the guid Tasker stores. */
data class TaskerItem(
    val label: String,
    val guid: String,
)

/**
 * The Tasker plugin's setting screen (3.2.11, PattNG ui/shortcut/TaskerActivity):
 * Tasker opens it to edit the setting — pick start or stop, and which
 * profile — and it answers with the bundle Tasker fires later, plus the
 * blurb Tasker shows for the setting.
 *
 * Lite adaptations: the profiles come from ProfileRepository (PattNG
 * reads them from MMKV); the theme follows the stored mode, the way
 * MainActivity applies it.
 */
class TaskerActivity : ComponentActivity() {

    private val items = mutableListOf<TaskerItem>()

    private var switchState by mutableStateOf(false)
    private var selectedPosition by mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme follows the stored mode, as in MainActivity.
        val themeMode = runBlocking { SettingsStore(applicationContext).themeModeNow() }
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        LiteColors.applyDark(when (themeMode) { "light" -> false; "dark" -> true; else -> systemDark })
        items.add(TaskerItem(label = "Default", guid = TaskerLauncher.TASKER_DEFAULT_GUID))
        lifecycleScope.launch(Dispatchers.IO) {
            ProfileRepository(applicationContext).loadProfiles()
                .forEach { items.add(TaskerItem(label = it.name, guid = it.id)) }
            withContext(Dispatchers.Main) {
                initSelection()
                setContent { TaskerScreen() }
            }
        }
    }

    private fun initSelection() {
        try {
            val bundle = intent?.getBundleExtra(TaskerLauncher.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(TaskerLauncher.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(TaskerLauncher.TASKER_EXTRA_BUNDLE_GUID, "")

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else {
                switchState = switch
                selectedPosition = items.indexOfFirst { it.guid == guid.toString() }
            }
        } catch (e: Exception) {
            AppLog.w("TaskerActivity", "failed to initialize Tasker settings: ${e.message}")
        }
    }

    private fun confirmFinish() {
        val position = selectedPosition
        if (position < 0) {
            // 1.0.1.2 (#105): Save with nothing selected used to do
            // nothing at all — say so (the Save button is also disabled
            // until a row is picked, so this is the backstop).
            Toast.makeText(this, R.string.tasker_select_profile, Toast.LENGTH_SHORT).show()
            return
        }

        val extraBundle = Bundle()
        extraBundle.putBoolean(TaskerLauncher.TASKER_EXTRA_BUNDLE_SWITCH, switchState)
        extraBundle.putString(TaskerLauncher.TASKER_EXTRA_BUNDLE_GUID, items[position].guid)
        val intent = Intent()

        val blurb = getString(
            if (switchState) R.string.tasker_blurb_start else R.string.tasker_blurb_stop,
            items[position].label
        )

        intent.putExtra(TaskerLauncher.TASKER_EXTRA_BUNDLE, extraBundle)
        intent.putExtra(TaskerLauncher.TASKER_EXTRA_STRING_BLURB, blurb)
        setResult(RESULT_OK, intent)
        finish()
    }

    @Composable
    private fun TaskerScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { finish() }) {
                    Text(
                        text = stringResource(R.string.sub_import_cancel),
                        fontSize = 14.sp,
                        color = LiteColors.textSecondary,
                        fontFamily = Vazirmatn
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { confirmFinish() },
                    enabled = selectedPosition >= 0,
                ) {
                    Text(
                        text = stringResource(R.string.server_edit_save),
                        fontSize = 14.sp,
                        color = LiteColors.accent,
                        fontFamily = Vazirmatn
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { switchState = !switchState }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tasker_start_service),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LiteColors.text,
                    fontFamily = Vazirmatn,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = switchState,
                    onCheckedChange = { switchState = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LiteColors.accent,
                        checkedTrackColor = LiteColors.accent.copy(alpha = 0.4f)
                    )
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items, key = { _, item -> item.guid }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPosition = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPosition == index,
                            onClick = { selectedPosition = index },
                            colors = RadioButtonDefaults.colors(selectedColor = LiteColors.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.label,
                            fontSize = 15.sp,
                            color = LiteColors.text,
                            fontFamily = Vazirmatn
                        )
                    }
                }
            }
        }
    }
}
