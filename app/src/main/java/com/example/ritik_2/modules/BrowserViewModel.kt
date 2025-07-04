package com.example.ritik_2.modules

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel

// Data Classes and Models
data class SavedPassword(
    val id: String = "",
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class BookmarkItem(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// Repository for managing passwords and bookmarks
class BrowserRepository {
    private val _savedPasswords = mutableStateListOf<SavedPassword>()
    private val _bookmarks = mutableStateListOf<BookmarkItem>()

    val savedPasswords: List<SavedPassword> = _savedPasswords
    val bookmarks: List<BookmarkItem> = _bookmarks

    fun savePassword(website: String, username: String, password: String) {
        val newPassword = SavedPassword(
            id = generateId(),
            website = website,
            username = username,
            password = password
        )
        _savedPasswords.add(newPassword)
    }

    fun deletePassword(id: String) {
        _savedPasswords.removeIf { it.id == id }
    }

    fun getPasswordsForWebsite(website: String): List<SavedPassword> {
        return _savedPasswords.filter { it.website.contains(website, ignoreCase = true) }
    }

    fun addBookmark(title: String, url: String) {
        val bookmark = BookmarkItem(
            id = generateId(),
            title = title,
            url = url
        )
        _bookmarks.add(bookmark)
    }

    fun deleteBookmark(id: String) {
        _bookmarks.removeIf { it.id == id }
    }

    private fun generateId(): String {
        return System.currentTimeMillis().toString() + (0..999).random()
    }
}

// ViewModel for managing browser state
class BrowserViewModel(private val repository: BrowserRepository = BrowserRepository()) : ViewModel() {
    private val _currentUrl = mutableStateOf("https://www.google.com")
    private val _isLoading = mutableStateOf(false)
    private val _showPasswordDialog = mutableStateOf(false)
    private val _showBookmarkDialog = mutableStateOf(false)
    private val _showPasswordManager = mutableStateOf(false)
    private val _canGoBack = mutableStateOf(false)
    private val _canGoForward = mutableStateOf(false)
    private val _pageTitle = mutableStateOf("New Tab")

    val currentUrl: MutableState<String> = _currentUrl
    val isLoading: MutableState<Boolean> = _isLoading
    val showPasswordDialog: MutableState<Boolean> = _showPasswordDialog
    val showBookmarkDialog: MutableState<Boolean> = _showBookmarkDialog
    val showPasswordManager: MutableState<Boolean> = _showPasswordManager
    val canGoBack: MutableState<Boolean> = _canGoBack
    val canGoForward: MutableState<Boolean> = _canGoForward
    val pageTitle: MutableState<String> = _pageTitle

    val savedPasswords = repository.savedPasswords
    val bookmarks = repository.bookmarks

    fun navigateToUrl(url: String) {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(".") && !url.contains(" ")) {
                "http://192.168.7.247/nagios/"
            } else {
                "https://www.google.com/search?q=${url.replace(" ", "+")}"
            }
        } else {
            url
        }
        _currentUrl.value = formattedUrl
        _isLoading.value = true
    }

    fun onPageFinished(title: String?) {
        _isLoading.value = false
        _pageTitle.value = title ?: "Web Page"
    }

    fun onPageStarted() {
        _isLoading.value = true
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _canGoBack.value = canGoBack
        _canGoForward.value = canGoForward
    }

    fun showPasswordDialog() {
        _showPasswordDialog.value = true
    }

    fun hidePasswordDialog() {
        _showPasswordDialog.value = false
    }

    fun showBookmarkDialog() {
        _showBookmarkDialog.value = true
    }

    fun hideBookmarkDialog() {
        _showBookmarkDialog.value = false
    }

    fun showPasswordManager() {
        _showPasswordManager.value = true
    }

    fun hidePasswordManager() {
        _showPasswordManager.value = false
    }

    fun savePassword(website: String, username: String, password: String) {
        repository.savePassword(website, username, password)
        hidePasswordDialog()
    }

    fun deletePassword(id: String) {
        repository.deletePassword(id)
    }

    fun addBookmark(title: String, url: String) {
        repository.addBookmark(title, url)
        hideBookmarkDialog()
    }

    fun deleteBookmark(id: String) {
        repository.deleteBookmark(id)
    }

    fun getPasswordsForCurrentSite(): List<SavedPassword> {
        return repository.getPasswordsForWebsite(extractDomain(_currentUrl.value))
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
}