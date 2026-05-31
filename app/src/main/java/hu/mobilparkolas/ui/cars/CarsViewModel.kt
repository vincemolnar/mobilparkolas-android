package hu.mobilparkolas.ui.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hu.mobilparkolas.data.repo.CarRepository
import hu.mobilparkolas.domain.model.Car
import kotlinx.coroutines.launch

class CarsViewModel(private val repo: CarRepository) : ViewModel() {

    val cars = repo.cars

    fun add(plate: String, name: String, makeDefault: Boolean) {
        if (plate.isBlank()) return
        viewModelScope.launch {
            repo.save(Car(plate = plate.trim(), name = name.trim(), isDefault = makeDefault))
        }
    }

    fun setDefault(car: Car) {
        viewModelScope.launch { repo.save(car.copy(isDefault = true)) }
    }

    fun delete(car: Car) {
        viewModelScope.launch { repo.delete(car) }
    }

    class Factory(private val repo: CarRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CarsViewModel(repo) as T
    }
}
