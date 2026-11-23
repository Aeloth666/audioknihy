package cz.audioknihy.ui.book

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cz.audioknihy.R
import cz.audioknihy.covers.CoverSearch
import cz.audioknihy.library.Book
import cz.audioknihy.ui.Cover
import cz.audioknihy.ui.LocalCovers
import kotlinx.coroutines.launch

/** Ruční hledání obalu: upravitelný dotaz, mřížka nalezených obalů, klepnutím vybrat. */
@Composable
fun CoverPickerDialog(book: Book, onDismiss: () -> Unit) {
    val covers = LocalCovers.current ?: return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(CoverSearch.cleanTitle(book.title, book.author)) }
    var author by remember { mutableStateOf(book.author) }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun search() {
        loading = true
        failed = false
        scope.launch {
            results = runCatching { covers.candidates(query, author) }.onFailure { failed = true }.getOrNull()
            loading = false
        }
    }
    LaunchedEffect(Unit) { search() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.find_cover)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.cover_query)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.cover_author)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp), contentAlignment = Alignment.Center) {
                    val list = results
                    when {
                        loading -> CircularProgressIndicator()
                        failed -> Text(stringResource(R.string.cover_search_failed), color = MaterialTheme.colorScheme.error)
                        list.isNullOrEmpty() -> Text(stringResource(R.string.cover_none_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list) { url ->
                                Cover(
                                    url,
                                    Modifier.aspectRatio(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clickable {
                                            scope.launch {
                                                covers.setOverride(book, url)
                                                onDismiss()
                                            }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = { scope.launch { covers.clearOverride(book); onDismiss() } }) {
                Text(stringResource(R.string.cover_reset))
            }
        },
    )
}
