package com.amyrmo.damavand.lite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liteDataStore: DataStore<Preferences> by preferencesDataStore(name = "lite_settings")

/**
 * Lite settings (DataStore). Defaults follow the design spec:
 * kill switch OFF, auto-connect OFF, battery saver ON, subscription
 * auto-update ON, preferred core AUTO.
 */
class SettingsStore(private val context: Context) {

    companion object {
        val KEY_KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KEY_BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val KEY_SUB_AUTO_UPDATE = booleanPreferencesKey("sub_auto_update")
        val KEY_PREFERRED_CORE = stringPreferencesKey("preferred_core")
        val KEY_SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val KEY_LAST_WINNER_LINK = stringPreferencesKey("last_winner_link")
    }

    val killSwitch: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_KILL_SWITCH] ?: false }
    val autoConnect: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_AUTO_CONNECT] ?: false }
    val batterySaver: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_BATTERY_SAVER] ?: true }
    val subAutoUpdate: Flow<Boolean> = context.liteDataStore.data.map { it[KEY_SUB_AUTO_UPDATE] ?: true }
    val preferredCore: Flow<String> = context.liteDataStore.data.map { it[KEY_PREFERRED_CORE] ?: "auto" }
    val selectedProfileId: Flow<String?> = context.liteDataStore.data.map { it[KEY_SELECTED_PROFILE_ID] }
    val lastWinnerLink: Flow<String?> = context.liteDataStore.data.map { it[KEY_LAST_WINNER_LINK] }

    suspend fun setKillSwitch(v: Boolean) = context.liteDataStore.edit { it[KEY_KILL_SWITCH] = v }
    suspend fun setAutoConnect(v: Boolean) = context.liteDataStore.edit { it[KEY_AUTO_CONNECT] = v }
    suspend fun setBatterySaver(v: Boolean) = context.liteDataStore.edit { it[KEY_BATTERY_SAVER] = v }
    suspend fun setSubAutoUpdate(v: Boolean) = context.liteDataStore.edit { it[KEY_SUB_AUTO_UPDATE] = v }
    suspend fun setPreferredCore(v: String) = context.liteDataStore.edit { it[KEY_PREFERRED_CORE] = v }
    suspend fun setSelectedProfileId(v: String?) {
        context.liteDataStore.edit {
            if (v == null) it.remove(KEY_SELECTED_PROFILE_ID) else it[KEY_SELECTED_PROFILE_ID] = v
        }
    }
    suspend fun setLastWinnerLink(v: String?) {
        context.liteDataStore.edit {
            if (v == null) it.remove(KEY_LAST_WINNER_LINK) else it[KEY_LAST_WINNER_LINK] = v
        }
    }

    suspend fun killSwitchNow(): Boolean = killSwitch.first()
    suspend fun autoConnectNow(): Boolean = autoConnect.first()
    suspend fun batterySaverNow(): Boolean = batterySaver.first()
    suspend fun subAutoUpdateNow(): Boolean = subAutoUpdate.first()
    suspend fun selectedProfileIdNow(): String? = selectedProfileId.first()
}
