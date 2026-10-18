package com.apex.terminal.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.apex.terminal.util.ServiceLocator
import com.apex.terminal.ui.chart.ChartViewModel

/** Manual DI wiring; keeps Hilt/Koin out of the dependency graph. */
class ChartViewModelFactory(
    private val application: Application,
    private val locator: ServiceLocator,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChartViewModel::class.java)) {
            "Unknown ViewModel $modelClass"
        }
        return ChartViewModel(
            application = application,
            repository = locator.marketRepository,
            settingsStore = locator.settingsStore,
            catalog = locator.symbolCatalog,
            monitorController = locator.monitorController,
        ) as T
    }
}
