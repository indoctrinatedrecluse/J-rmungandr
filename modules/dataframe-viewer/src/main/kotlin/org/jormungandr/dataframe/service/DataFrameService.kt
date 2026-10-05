package org.jormungandr.dataframe.service

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.dataframe.model.DataFrame
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class DataFrameService {

    private val datasets = ConcurrentHashMap<String, DataFrame>()
    private val _activeDataFrame = MutableStateFlow<DataFrame?>(null)
    val activeDataFrame: StateFlow<DataFrame?> = _activeDataFrame.asStateFlow()

    fun registerDataFrame(name: String, df: DataFrame) {
        datasets[name] = df
        _activeDataFrame.value = df
    }

    fun getDataFrame(name: String): DataFrame? = datasets[name]

    fun getAllDataFrames(): List<DataFrame> = datasets.values.toList()

    fun removeDataFrame(name: String) {
        datasets.remove(name)
        if (_activeDataFrame.value?.name == name) {
            _activeDataFrame.value = datasets.values.firstOrNull()
        }
    }

    fun clear() {
        datasets.clear()
        _activeDataFrame.value = null
    }
}
