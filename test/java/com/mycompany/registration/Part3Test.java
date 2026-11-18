package com.mycompany.registration;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each test uses the lecturer's five example messages, not hard-coded application content. */
class Part3Test {
    private static final String LONG =
            "Where are you? You are late! I have asked you to be on time.";
    private static final String RECIPIENT = "+27838884567";
    @TempDir Path directory;
    private QuickChat chat;
    private Message m1, m2, m3, m4, m5;

    @BeforeEach
    void populateThroughNormalAppMethods() throws IOException {
        chat = new QuickChat(directory.resolve("messages.json"));
        m1 = new Message("A Student", "+27834557896", "Did you get the cake?", 0, "0012345678");
        m2 = new Message("A Student", RECIPIENT, LONG, 1, "1112345678");
        m3 = new Message("A Student", "+27834484567", "Yohoooo, I am at your gate.", 2, "2212345678");
        // The Part 3 table says 'Developer 0838884567' but does not specify
        // Message 4's recipient. Its ID is set to the explicit ID used in the search test.
        m4 = new Message("Developer", RECIPIENT, "It is dinner time!", 3, "0838884567");
        m5 = new Message("A Student", RECIPIENT, "Ok, I am leaving without you.", 4, "4412345678");
        chat.sendMessage(m1);
        chat.storeMessage(m2);
        chat.discardMessage(m3);
        chat.sendMessage(m4);
        chat.storeMessage(m5);
    }

    @Test
    void arraysAreDynamicallyPopulatedWithAllFiveCategories() {
        assertEquals(2, chat.getSentMessagesArray().length);
        assertEquals(1, chat.getDiscardedMessagesArray().length);
        assertEquals(2, chat.getStoredMessagesArray().length);
        assertEquals(5, chat.getMessageHashesArray().length);
        assertEquals(5, chat.getMessageIDsArray().length);
        assertEquals(2, chat.returnTotalMessages());
        assertEquals("Did you get the cake?", chat.getSentMessagesArray()[0].getMessage());
        assertEquals("It is dinner time!", chat.getSentMessagesArray()[1].getMessage());
    }

    @Test
    void storedSenderRecipientDetailsIncludeBothStoredRecords() {
        String report = chat.displayStoredSendersAndRecipients();
        assertTrue(report.contains("Sender: A Student"));
        assertEquals(2, report.split("Recipient:", -1).length - 1);
    }

    @Test
    void longestStoredMessageIsTestMessageTwo() {
        assertEquals(LONG, chat.findLongestStoredMessage().getMessage());
    }

    @Test
    void searchMessageIdFindsLecturerProvidedDeveloperId() {
        assertEquals("It is dinner time!", chat.findMessageByID("0838884567").getMessage());
        assertNull(chat.findMessageByID("9999999999"));
    }

    @Test
    void recipientSearchReturnsSentAndStoredAndNeverDisregarded() {
        Message[] found = chat.findMessagesForRecipient(RECIPIENT);
        assertEquals(3, found.length); // stored 2, sent 1 (recipient for message 4 is an assumed fixture)
        assertEquals(LONG, found[0].getMessage());
        assertEquals("It is dinner time!", found[1].getMessage());
        assertEquals("Ok, I am leaving without you.", found[2].getMessage());
        assertEquals(2, chat.findStoredMessagesForRecipient(RECIPIENT).length);
    }

    @Test
    void deletingStoredMessageByHashAlsoRemovesItFromJson() throws IOException {
        assertEquals("Message: \"" + LONG + "\" successfully deleted.",
                chat.deleteMessageByHash(m2.getMessageHash()));
        assertEquals(1, chat.getStoredMessagesArray().length);
        assertNull(chat.findMessageByID(m2.getMessageID()));
        assertEquals(4, chat.getMessageIDsArray().length);
        assertFalse(Files.readString(directory.resolve("messages.json")).contains(LONG));
        assertTrue(Files.readString(directory.resolve("messages.json")).contains("Ok, I am leaving"));
    }

    @Test
    void reportContainsStoredAndSentMessageHashesRecipientsAndContents() {
        String report = chat.displayFullReport();
        assertTrue(report.contains("=== STORED MESSAGES REPORT ==="));
        assertTrue(report.contains("=== SENT MESSAGES REPORT ==="));
        assertTrue(report.contains(m1.getMessageHash()));
        assertTrue(report.contains(m2.getMessageHash()));
        assertTrue(report.contains("Recipient: " + RECIPIENT));
        assertTrue(report.contains("Message: It is dinner time!"));
    }

    @Test
    void storedMessagesLoadFromJsonWhenQuickChatRestarts() {
        QuickChat loaded = new QuickChat(directory.resolve("messages.json"));
        assertEquals(2, loaded.getStoredMessagesArray().length);
        assertEquals(m2.getMessageHash(), loaded.getStoredMessagesArray()[0].getMessageHash());
        assertEquals(m5.getMessageID(), loaded.getStoredMessagesArray()[1].getMessageID());
        assertEquals(LONG, loaded.findLongestStoredMessage().getMessage());
        assertEquals(0, loaded.returnTotalMessages()); // sent messages are not saved by Part 2
    }

    @Test
    void olderPart2JsonFormatLoadsEvenWithoutSenderOrSequenceNumber() throws IOException {
        Path output = directory.resolve("old.json");
        Files.writeString(output, "[{\"messageID\":\"0012345678\","
                + "\"recipientCell\":\"+27834557896\",\"messageLength\":5,"
                + "\"messageHash\":\"00:8:HELLOHELLO\",\"message\":\"Hello\"}]");
        QuickChat loaded = new QuickChat(output);
        assertEquals(1, loaded.getStoredMessagesArray().length);
        assertEquals("Not recorded", loaded.getStoredMessagesArray()[0].getSender());
        assertEquals(8, loaded.getStoredMessagesArray()[0].getSequenceNumber());
    }

    @Test
    void jsonSpecialCharactersSurviveSaveReloadAndDelete() throws IOException {
        Message example = new Message("A Student", RECIPIENT,
                "Quote \"x\" with a slash \\ and a line\nbreak", 5, "5512345678");
        chat.storeMessage(example);
        QuickChat reloaded = new QuickChat(directory.resolve("messages.json"));
        assertEquals(example.getMessage(), reloaded.findMessageByID(example.getMessageID()).getMessage());
        String saved = Files.readString(directory.resolve("messages.json"), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\\\"x\\\""));
        assertTrue(saved.contains("\\n"));
    }

    @Test
    void invalidJsonDoesNotDestroyExistingFile() throws IOException {
        Path invalid = directory.resolve("damaged.json");
        Files.writeString(invalid, "[not-json");
        assertThrows(java.io.UncheckedIOException.class, () -> new QuickChat(invalid));
        assertEquals("[not-json", Files.readString(invalid));
    }

    @Test
    void missingRecipientsAndHashesHaveFriendlyOutcomes() throws IOException {
        assertEquals(0, chat.findMessagesForRecipient("+27999999999").length);
        assertEquals("Message hash not found.", chat.deleteMessageByHash("not-a-hash"));
        assertEquals(2, chat.getStoredMessagesArray().length);
    }
}
