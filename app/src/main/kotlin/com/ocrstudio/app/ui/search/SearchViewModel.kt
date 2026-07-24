package com.ocrstudio.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.database.dao.PageSearchHit
import com.ocrstudio.core.database.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val recentSearchesRepository: RecentSearchesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<PageSearchHit>>(emptyList())
    val results: StateFlow<List<PageSearchHit>> = _results

    val recentSearches: StateFlow<List<String>> = recentSearchesRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _results.value = emptyList()
            return
        }
        viewModelScope.launch {
            _results.value = searchRepository.search(newQuery)
        }
    }

    /** Called when the user commits a search (IME "search" action), not on every keystroke --
     *  otherwise every partial word typed would get recorded as its own recent search. */
    fun onSearchSubmit() {
        val current = _query.value
        if (current.isBlank()) return
        viewModelScope.launch { recentSearchesRepository.record(current) }
    }
}
