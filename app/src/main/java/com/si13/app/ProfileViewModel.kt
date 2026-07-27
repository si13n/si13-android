package com.si13.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val user: AuthUser? = null,
    val isOnline: Boolean = false,
    val completedTaskCount: Int = 0,
    val activeTaskCount: Int = 0,
    val completionRate: Int = 0
) {
    val showProfileCard: Boolean get() = user != null && isOnline
    val showSignOut: Boolean get() = user != null
}

data class ProfileProgress(val completed: Int, val active: Int, val rate: Int) {
    companion object {
        fun from(tasks: List<Task>): ProfileProgress {
            val completed = tasks.count { it.completed }
            return ProfileProgress(
                completed = completed,
                active = tasks.size - completed,
                rate = if (tasks.isEmpty()) 0 else completed * 100 / tasks.size
            )
        }
    }
}

class ProfileViewModel(
    users: Flow<AuthUser?>,
    connectivity: Flow<Boolean>,
    taskRepositoryFactory: () -> TaskRepository,
    private val signOutAction: () -> Unit
) : ViewModel() {
    private val taskCounts = users.flatMapLatest {
        taskRepositoryFactory().observeTasks()
    }.map(ProfileProgress::from)

    val uiState = combine(users, connectivity, taskCounts) { user, online, counts ->
        ProfileUiState(user, online, counts.completed, counts.active, counts.rate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun signOut() = signOutAction()

    class Factory(
        private val users: Flow<AuthUser?>,
        private val connectivity: Flow<Boolean>,
        private val taskRepositoryFactory: () -> TaskRepository,
        private val signOutAction: () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(users, connectivity, taskRepositoryFactory, signOutAction) as T
    }
}
