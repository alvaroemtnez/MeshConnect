package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chat long-press card, multi-select, and deleting chats (2026-11-10). */
class ChatActionsWiringTest {

    private val chats = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChatsScreen.kt").readText()
    private val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()

    @Test
    fun `the card offers edit, select and a red delete, and asks before deleting`() {
        assertTrue(chats.contains("ChatMenuRow(R.drawable.ic_select, stringResource(R.string.conv_select))"))
        assertTrue(chats.contains("ChatMenuRow(R.drawable.ic_delete, stringResource(R.string.delete), destructive = true)"))
        // A channel edits as in Settings.
        assertTrue(chats.contains("if (row.isChannel) editChannel = row.key.toIntOrNull() else nicknameFor = row"))
        assertTrue(chats.contains("ChannelEditSheet(vm, ch, onDismiss = { editChannel = null })"))
        // Nothing deletes without the dialog, and the dialog says when channels leave the radio.
        assertTrue(chats.contains("vm.deleteChats(rows)"))
        assertTrue(chats.contains("if (rows.any { it.isChannel }) R.string.chats_delete_body_channels else R.string.chats_delete_body"))
    }

    @Test
    fun `deleting a channel removes it from the radio and takes its slot's messages`() {
        val delete = vm.substringAfter("fun deleteChats(rows: List<ConversationRow>) {").substringBefore("\n    }\n")
        assertTrue(delete.contains("deleteChannel(it)"))
        assertTrue(delete.contains("clearThread(row.kind, row.key)"))
        // The radio reuses the slot; its old posts must not appear under the next channel.
        val channel = vm.substringAfter("fun deleteChannel(index: Int) {").substringBefore("\n    }\n")
        assertTrue(channel.contains("db.messages().clearThread(selfKey.value, MessageRepository.KIND_CHANNEL, index.toString())"))
    }
}
