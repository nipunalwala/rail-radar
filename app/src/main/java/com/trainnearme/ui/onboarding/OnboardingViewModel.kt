package com.trainnearme.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainnearme.core.data.SettingsRepository
import com.trainnearme.core.permissions.PermissionChecker
import com.trainnearme.core.permissions.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val checker: PermissionChecker,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _permissions = MutableStateFlow(checker.current())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    /** Call after a permission dialog closes or the screen comes back to the front. */
    fun refresh() {
        _permissions.value = checker.current()
    }

    fun finish() {
        viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } }
    }
}
