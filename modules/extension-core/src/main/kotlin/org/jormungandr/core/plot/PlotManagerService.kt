package org.jormungandr.core.plot

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-wide service coordinating graphical plots across Jupyter, Matplotlib,
 * Seaborn, and DataFrame Studio.
 */
@Service(Service.Level.APP)
class PlotManagerService {

    private val _plots = MutableStateFlow<List<PlotItem>>(emptyList())
    val plots: StateFlow<List<PlotItem>> = _plots.asStateFlow()

    private val _activePlot = MutableStateFlow<PlotItem?>(null)
    val activePlot: StateFlow<PlotItem?> = _activePlot.asStateFlow()

    private val _webPlots = MutableStateFlow<List<WebPlotItem>>(emptyList())
    val webPlots: StateFlow<List<WebPlotItem>> = _webPlots.asStateFlow()

    private val _activeWebPlot = MutableStateFlow<WebPlotItem?>(null)
    val activeWebPlot: StateFlow<WebPlotItem?> = _activeWebPlot.asStateFlow()

    fun addPlot(plot: PlotItem) {
        val current = _plots.value.toMutableList()
        current.add(plot)
        _plots.value = current
        _activePlot.value = plot
    }

    fun selectPlot(plot: PlotItem) {
        _activePlot.value = plot
    }

    fun removePlot(id: String) {
        val current = _plots.value.toMutableList()
        current.removeAll { it.id == id }
        _plots.value = current
        if (_activePlot.value?.id == id) {
            _activePlot.value = current.lastOrNull()
        }
    }

    fun addWebPlot(plot: WebPlotItem) {
        val current = _webPlots.value.toMutableList()
        current.add(plot)
        _webPlots.value = current
        _activeWebPlot.value = plot
    }

    fun selectWebPlot(plot: WebPlotItem) {
        _activeWebPlot.value = plot
    }

    fun removeWebPlot(id: String) {
        val current = _webPlots.value.toMutableList()
        current.removeAll { it.id == id }
        _webPlots.value = current
        if (_activeWebPlot.value?.id == id) {
            _activeWebPlot.value = current.lastOrNull()
        }
    }

    fun clearAllWebPlots() {
        _webPlots.value = emptyList()
        _activeWebPlot.value = null
    }

    fun clearAll() {
        _plots.value = emptyList()
        _activePlot.value = null
        _webPlots.value = emptyList()
        _activeWebPlot.value = null
    }

    companion object {
        fun getInstance(): PlotManagerService {
            return ApplicationManager.getApplication().getService(PlotManagerService::class.java)
        }
    }
}
