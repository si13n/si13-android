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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProfileUiState(
    val user: AuthUser? = null,
    val isOnline: Boolean = false,
    val completedTaskCount: Int = 0,
    val completedToday: Int = 0,
    val completedThisWeek: Int = 0,
    val activeTaskCount: Int = 0,
    val completionRate: Int = 0,
    val weeklyActivity: List<Int> = List(7) { 0 }
) {
    val showProfileCard: Boolean get() = user != null
    val showSignOut: Boolean get() = user != null
}

data class ProfileProgress(
    val completed: Int,
    val active: Int,
    val rate: Int,
    val completedToday: Int = 0,
    val completedThisWeek: Int = 0,
    val weeklyActivity: List<Int> = List(7) { 0 }
) {
    companion object {
        fun from(tasks: List<Task>): ProfileProgress {
            val completed = tasks.count { it.completed }
            val today = LocalDate.now()
            val completedDates = tasks.filter(Task::completed).mapNotNull { task ->
                (task.completedAt ?: task.updatedAt).takeIf { it > 0 }?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            return ProfileProgress(
                completed = completed,
                active = tasks.size - completed,
                rate = if (tasks.isEmpty()) 0 else completed * 100 / tasks.size,
                completedToday = completedDates.count { it == today },
                completedThisWeek = completedDates.count { !it.isBefore(weekStart) && !it.isAfter(today) },
                weeklyActivity = (6 downTo 0).map { offset -> completedDates.count { it == today.minusDays(offset.toLong()) } }
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
        ProfileUiState(
            user = user,
            isOnline = online,
            completedTaskCount = counts.completed,
            completedToday = counts.completedToday,
            completedThisWeek = counts.completedThisWeek,
            activeTaskCount = counts.active,
            completionRate = counts.rate,
            weeklyActivity = counts.weeklyActivity
        )
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
