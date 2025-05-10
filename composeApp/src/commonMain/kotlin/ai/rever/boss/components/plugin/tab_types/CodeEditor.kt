package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabTypeInfo
import ai.rever.boss.components.registery.TabTypeId
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext

@Composable
fun CodeEditorUI(fileId: String, content: String = "", onContentChange: (String) -> Unit = {}) {
    // Your editor implementation goes here
    // This could use a code editor library or custom implementation
    TextField(
        value = content,
        onValueChange = onContentChange,
        modifier = Modifier.fillMaxSize()
    )
}

object CodeEditor: TabTypeInfo {
    override val typeId = TabTypeId("editor")
    override val displayName = "Code Editor"
    override val icon = Icons.Outlined.Code
}

class CodeEditorTabComponent(
    override val config: TabInfo,
    componentContext: ComponentContext
) : TabComponentWithUI, ComponentContext by componentContext {

    // In a real implementation, this would hold editor state
    private var editorContent = mutableStateOf("")

    override val tabTypeInfo = CodeEditor

    @Composable
    override fun Content() {
        CodeEditorUI(
            fileId = config.id,
            content = editorContent.value,
            onContentChange = { editorContent.value = it }
        )
    }
}

fun DefaultPlugin.registerCodeEditor() = tabRegistry.registerTabType(CodeEditor) {
    tabInfo, ctx -> CodeEditorTabComponent(tabInfo, ctx)
}
