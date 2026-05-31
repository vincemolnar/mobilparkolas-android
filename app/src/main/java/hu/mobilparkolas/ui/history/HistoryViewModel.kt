package hu.mobilparkolas.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hu.mobilparkolas.data.repo.ParkingRepository
import hu.mobilparkolas.domain.model.ParkingSession
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: ParkingRepository) : ViewModel() {

    val history = repo.history

    fun delete(session: ParkingSession) {
        viewModelScope.launch { repo.delete(session) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.deleteAll() }
    }

    class Factory(private val repo: ParkingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repo) as T
    }
}
