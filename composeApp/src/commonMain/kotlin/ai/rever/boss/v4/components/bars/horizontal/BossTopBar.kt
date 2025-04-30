package ai.rever.boss.v4.components.bars.horizontal

import BossDarkBorder
import BossDarkTextPrimary
import ai.rever.boss.v4.components.buttons.BossActionButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.GitPullRequest

@Composable
fun BossTopBar() {
    HorizontalBar(40.dp) {
        HorizontalBarRow(modifier = Modifier.fillMaxHeight().padding(start = 36.dp)) {
            BossTopLeftBar()
            Spacer(modifier = Modifier.weight(1f))
            BossTopRunBar()
            Spacer(modifier = Modifier.weight(0.1f))
            BossTopRightBar()
        }
    }
    Divider(color = BossDarkBorder)
}

@Composable
fun Logo(name: String) {
    Surface(
        modifier = Modifier
            .padding(2.dp)
            .height(22.dp)
            .width(22.dp)
        ,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF3592C4),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = name.substring(0, 2).uppercase(),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun BossActionButtonWithLogo(text: String, onClick: () -> Unit) {
    BossActionButton(
        leftLogo = { Logo(text) },
        text = text,
        onClick = onClick
    )
}

@Composable
fun BossTopLeftBar() {
    BossActionButtonWithLogo("Nycbs") { }
    BossActionButton(leftIcon = FeatherIcons.GitBranch, text = "main") { }
}

@Composable
fun BossTopRunBar() {
    BossActionButton(leftIcon = Icons.Outlined.Diversity2, text = "lanager [boss]") { }
    BossActionButton(imageVector = Icons.Outlined.PlayArrow, text = "Run", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.BugReport, text = "Bug", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Stop, text = "Stop", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.MoreVert, text = "Stop", onClick = {})
}

@Composable
fun BossTopRightBar() {
    BossActionButton(imageVector = Icons.Outlined.PersonAdd, text = "Sign Out", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Search, text = "Search", onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Settings, text = "Settings", onClick = {})
}