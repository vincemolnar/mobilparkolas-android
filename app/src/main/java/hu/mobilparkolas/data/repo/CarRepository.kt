package hu.mobilparkolas.data.repo

import hu.mobilparkolas.data.db.CarDao
import hu.mobilparkolas.data.db.CarEntity
import hu.mobilparkolas.domain.model.Car
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CarRepository(private val dao: CarDao) {

    val cars: Flow<List<Car>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getDefault(): Car? = dao.getDefault()?.toDomain()

    suspend fun getById(id: Long): Car? = dao.getById(id)?.toDomain()

    suspend fun save(car: Car) {
        if (car.isDefault) dao.clearDefaults()
        dao.upsert(car.toEntity())
    }

    suspend fun delete(car: Car) = dao.delete(car.toEntity())

    private fun CarEntity.toDomain() = Car(id, plate, name, isDefault)
    private fun Car.toEntity() = CarEntity(id, plate, name, isDefault)
}
