package ai.rever.boss
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Worklist(onNavigateToDetails: (work: Work) -> Unit, onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Header("Worklist", "BOSS", onNavigateBack)
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(works) { work ->
                WorkItem(work = work, onClick = { onNavigateToDetails(work) })
            }
        }
    }
}

@Composable
fun WorkItem(work: Work, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = work.longDescription,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = "Status: ${work.status}",
                style = MaterialTheme.typography.body1
            )
            Text(
                text = "Created: ${work.createdAt}",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

