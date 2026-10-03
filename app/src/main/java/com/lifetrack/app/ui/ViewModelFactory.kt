package com.lifetrack.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetrack.app.LifeTrackApp
import com.lifetrack.app.data.Repository

/** Gives any ViewModel access to the app's Repository without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (Repository) -> VM): VM {
    val app = LocalContext.current.applicationContext as LifeTrackApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(app.repository) as T
    })
}
