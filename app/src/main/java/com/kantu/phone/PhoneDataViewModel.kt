package com.kantu.phone

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactEntry(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val isDemo: Boolean = false,
) {
    val spokenLabel: String get() = name.ifBlank { number }
}

data class CallEntry(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val date: Long = 0,
    val isDemo: Boolean = false,
) {
    val spokenLabel: String get() = name.ifBlank { number }
}

interface ContactDirectory {
    val contacts: StateFlow<List<ContactEntry>>
    val history: StateFlow<List<CallEntry>>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>
    fun permissionsChanged()
}

class PhoneDataViewModel(application: Application) : AndroidViewModel(application), ContactDirectory {
    private val resolver: ContentResolver = application.contentResolver
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    private val _history = MutableStateFlow<List<CallEntry>>(emptyList())
    private val _loading = MutableStateFlow(false)
    override val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()
    override val history: StateFlow<List<CallEntry>> = _history.asStateFlow()
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error = _error.asStateFlow()
    private var refreshJob: Job? = null

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
        override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
    }

    override fun permissionsChanged() {
        if (observing) {
            resolver.unregisterContentObserver(observer)
            observing = false
        }
        if (hasDataPermissions()) {
            runCatching {
                resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                observing = true
            }
            if (hasHistoryPermission()) runCatching {
                resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
                observing = true
            }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        if (!hasDataPermissions()) {
            _contacts.value = emptyList()
            _history.value = emptyList()
            _loading.value = false
            return
        }
        refreshJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val loadedContacts = withContext(Dispatchers.IO) { queryContacts() }
                _contacts.value = loadedContacts
                // Call history is optional: its denial/provider failure must never hide contacts.
                _history.value = if (hasHistoryPermission()) {
                    try {
                        withContext(Dispatchers.IO) { queryHistory(loadedContacts) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                } else emptyList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _contacts.value = emptyList()
                _history.value = emptyList()
                _error.value = "暂时读不到联系人，请家人检查通讯录权限"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun hasDataPermissions(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasHistoryPermission() = ContextCompat.checkSelfPermission(
        getApplication<Application>(), Manifest.permission.READ_CALL_LOG,
    ) == PackageManager.PERMISSION_GRANTED

    private fun queryContacts(): List<ContactEntry> {
        val entries = LinkedHashMap<String, ContactEntry>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(projection[0])
            val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
            val numberIndex = cursor.getColumnIndexOrThrow(projection[2])
            val photoIndex = cursor.getColumnIndexOrThrow(projection[3])
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isBlank()) continue
                val key = normalize(number)
                if (!entries.containsKey(key)) {
                    entries[key] = ContactEntry(
                        id = cursor.getLong(idIndex),
                        name = cursor.getString(nameIndex).orEmpty(),
                        number = number,
                        photoUri = cursor.getString(photoIndex),
                    )
                }
            }
        }
        return entries.values.toList()
    }

    private fun queryHistory(contacts: List<ContactEntry>): List<CallEntry> {
        val byNumber = contacts.associateBy { normalize(it.number) }
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
        )
        val result = mutableListOf<CallEntry>()
        val seen = mutableSetOf<String>()
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(projection[0])
            val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
            val numberIndex = cursor.getColumnIndexOrThrow(projection[2])
            val dateIndex = cursor.getColumnIndexOrThrow(projection[3])
            while (result.size < 40 && cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isBlank() || number.startsWith("-") || !seen.add(normalize(number))) continue
                val contact = byNumber[normalize(number)]
                result += CallEntry(
                    id = cursor.getLong(idIndex),
                    name = cursor.getString(nameIndex).orEmpty().ifBlank { contact?.name.orEmpty() },
                    number = number,
                    photoUri = contact?.photoUri,
                    date = cursor.getLong(dateIndex),
                )
            }
        }
        return result
    }

    private fun normalize(number: String) = PhonePolicy.contactKey(number)

    override fun onCleared() {
        if (observing) resolver.unregisterContentObserver(observer)
        super.onCleared()
    }

}
