package com.tms.banking

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tms.banking.data.local.SecureCredentialStore
import com.tms.banking.data.local.TmsDatabase
import com.tms.banking.data.remote.TmsApi
import com.tms.banking.data.repository.AccountRepository
import com.tms.banking.data.repository.CategoryRepository
import com.tms.banking.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tms_settings")

object PrefsKeys {
    val BACKEND_URL = stringPreferencesKey("backend_url")
}

class AppContainer(private val context: Context) {
    private val db = TmsDatabase.getInstance(context)
    val credentialStore = SecureCredentialStore(context)

    val backendUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PrefsKeys.BACKEND_URL] ?: ""
    }

    suspend fun saveBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.BACKEND_URL] = url
        }
    }

    fun buildApi(baseUrl: String): TmsApi = TmsApi.create(TmsApi.sanitizeUrl(baseUrl))

    fun accountRepository(api: TmsApi) = AccountRepository(db.accountDao(), api)
    fun transactionRepository(api: TmsApi) = TransactionRepository(db.transactionDao(), api)
    fun categoryRepository(api: TmsApi) = CategoryRepository(db.categoryDao(), api)
}

class TmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
