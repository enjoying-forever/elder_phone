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

class PhoneDataViewModel(application: Application) : AndroidViewModel(application) {
    private val resolver: ContentResolver = application.contentResolver
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    private val _history = MutableStateFlow<List<CallEntry>>(emptyList())
    private val _loading = MutableStateFlow(false)
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()
    val history: StateFlow<List<CallEntry>> = _history.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
        override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
    }

    fun permissionsChanged() {
        if (hasDataPermissions() && !observing) {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            observing = true
        }
        refresh()
    }

    fun refresh() {
        if (!hasDataPermissions()) {
            _contacts.value = emptyList()
            _history.value = emptyList()
            return
        }
        viewModelScope.launch {
            _loading.value = true
            val loadedContacts = withContext(Dispatchers.IO) { queryContacts() }
            val loadedHistory = withContext(Dispatchers.IO) { queryHistory(loadedContacts) }
            _contacts.value = if (loadedContacts.isEmpty() && BuildConfig.DEBUG) demoContacts else loadedContacts
            _history.value = if (loadedHistory.isEmpty() && BuildConfig.DEBUG) demoHistory else loadedHistory
            _loading.value = false
        }
    }

    private fun hasDataPermissions(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }

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
                entries.putIfAbsent(
                    key,
                    ContactEntry(
                        id = cursor.getLong(idIndex),
                        name = cursor.getString(nameIndex).orEmpty(),
                        number = number,
                        photoUri = cursor.getString(photoIndex),
                    ),
                )
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
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
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

    private fun normalize(number: String) = number.filter(Char::isDigit).takeLast(11)

    override fun onCleared() {
        if (observing) resolver.unregisterContentObserver(observer)
        super.onCleared()
    }

    companion object {
        private val demoContacts = listOf(
            ContactEntry(-1, "女儿", "13800138001", isDemo = true),
            ContactEntry(-2, "社区医生", "13800138002", isDemo = true),
            ContactEntry(-3, "老朋友", "13800138003", isDemo = true),
        )
        private val demoHistory = listOf(
            CallEntry(-1, "女儿", "13800138001", isDemo = true),
            CallEntry(-2, "社区医生", "13800138002", isDemo = true),
        )
    }
}
