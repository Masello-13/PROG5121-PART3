package com.mycompany.registration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QuickChat state for Parts 2 and 3. The five Part 3 arrays are populated
 * dynamically; test data is never hard-coded in the application.
 */
public final class QuickChat {
    // The assignment explicitly requires arrays, rather than preset test data.
    private Message[] sentMessages = new Message[0];
    private Message[] discardedMessages = new Message[0];
    private Message[] storedMessages = new Message[0];
    private String[] messageHashes = new String[0];
    private String[] messageIDs = new String[0];
    private Message[] allMessages = new Message[0]; // parallel indexing for hash/ID arrays

    private final JsonMessageStore jsonStore;
    private final Set<String> processedIDs = new HashSet<>();

    /** Load saved JSON as stored messages when the application starts. */
    public QuickChat(Path storageFile) {
        jsonStore = new JsonMessageStore(storageFile);
        try {
            for (Message message : jsonStore.readAll()) {
                if (!processedIDs.add(message.getMessageID())) {
                    throw new IOException("Duplicate message IDs in stored JSON.");
                }
                storedMessages = append(storedMessages, message);
                addToIndexes(message);
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to read stored QuickChat data", error);
        }
    }

    public String sendMessage(Message message) {
        validateForSendOrStore(message);
        requireUnusedID(message);
        message.markSent();
        sentMessages = append(sentMessages, message);
        processedIDs.add(message.getMessageID());
        addToIndexes(message);
        return message.SentMessage();
    }

    public String discardMessage(Message message) {
        if (message == null) throw new IllegalArgumentException("Message cannot be null.");
        requireDraft(message);
        requireUnusedID(message);
        message.markDiscarded();
        discardedMessages = append(discardedMessages, message);
        processedIDs.add(message.getMessageID());
        addToIndexes(message);
        return message.SentMessage();
    }

    public String storeMessage(Message message) throws IOException {
        validateForSendOrStore(message);
        requireUnusedID(message);
        // Save first: on an I/O failure the message is still a draft.
        jsonStore.append(message);
        message.markStored();
        storedMessages = append(storedMessages, message);
        processedIDs.add(message.getMessageID());
        addToIndexes(message);
        return message.SentMessage();
    }

    private static void validateForSendOrStore(Message message) {
        if (message == null) throw new IllegalArgumentException("Message cannot be null.");
        requireDraft(message);
        if (!message.checkMessageID()) throw new IllegalArgumentException("Message ID must be ten digits.");
        if (!message.isRecipientValid()) throw new IllegalArgumentException(message.checkRecipientCell());
        if (!message.isMessageLengthValid()) throw new IllegalArgumentException(message.checkMessageLength());
    }

    private static void requireDraft(Message message) {
        if (message.getStatus() != Message.Status.DRAFT) {
            throw new IllegalArgumentException("This message has already been processed.");
        }
    }

    private void requireUnusedID(Message message) {
        if (processedIDs.contains(message.getMessageID())) {
            throw new IllegalArgumentException("A message with this ID has already been processed.");
        }
    }

    private static Message[] append(Message[] messages, Message message) {
        Message[] result = Arrays.copyOf(messages, messages.length + 1);
        result[result.length - 1] = message;
        return result;
    }

    private void addToIndexes(Message message) {
        allMessages = append(allMessages, message);
        messageHashes = Arrays.copyOf(messageHashes, messageHashes.length + 1);
        messageIDs = Arrays.copyOf(messageIDs, messageIDs.length + 1);
        messageHashes[messageHashes.length - 1] = message.getMessageHash();
        messageIDs[messageIDs.length - 1] = message.getMessageID();
    }

    /** Snapshots of the five arrays required by Part 3. */
    public Message[] getSentMessagesArray() { return sentMessages.clone(); }
    public Message[] getDiscardedMessagesArray() { return discardedMessages.clone(); }
    public Message[] getStoredMessagesArray() { return storedMessages.clone(); }
    public String[] getMessageHashesArray() { return messageHashes.clone(); }
    public String[] getMessageIDsArray() { return messageIDs.clone(); }

    /** Existing Part 2 API stays available for its tests. */
    public List<Message> getSentMessages() { return Collections.unmodifiableList(Arrays.asList(getSentMessagesArray())); }
    public List<Message> getDiscardedMessages() { return Collections.unmodifiableList(Arrays.asList(getDiscardedMessagesArray())); }
    public List<Message> getStoredMessages() { return Collections.unmodifiableList(Arrays.asList(getStoredMessagesArray())); }

    /** Number of messages currently recorded as successfully sent (not stored/discarded). */
    public int returnTotalMessages() { return sentMessages.length; }

    public String printMessages() {
        if (sentMessages.length == 0) return "No messages have been sent.";
        StringBuilder text = new StringBuilder();
        for (Message m : sentMessages) text.append(m.printMessages()).append("\n\n");
        return text.toString().trim();
    }

    /** Part 3(a): include sender and recipient for every saved message. */
    public String displayStoredSendersAndRecipients() {
        if (storedMessages.length == 0) return "No stored messages found.";
        StringBuilder text = new StringBuilder();
        for (Message m : storedMessages) {
            text.append("Sender: ").append(m.getSender())
                .append(" | Recipient: ").append(m.getRecipientCell()).append('\n');
        }
        return text.toString().trim();
    }

    /** Part 3(b): search the stored-message array for the longest message. */
    public Message findLongestStoredMessage() {
        Message longest = null;
        for (Message m : storedMessages) {
            if (longest == null || m.getMessageLength() > longest.getMessageLength()) longest = m;
        }
        return longest;
    }

    /** Part 3(c): search the parallel message-ID array, returning the matching message. */
    public Message findMessageByID(String id) {
        if (id == null) return null;
        for (int i = 0; i < messageIDs.length; i++) {
            if (messageIDs[i].equals(id.trim())) return allMessages[i];
        }
        return null;
    }

    /** Part 3(d) plus its unit-test wording: sent OR stored messages for a recipient. */
    public Message[] findMessagesForRecipient(String recipient) {
        if (recipient == null) return new Message[0];
        List<Message> results = new ArrayList<>();
        for (Message m : allMessages) {
            if (m.getStatus() != Message.Status.DISCARDED
                    && m.getRecipientCell().equals(recipient.trim())) results.add(m);
        }
        return results.toArray(new Message[0]);
    }

    /** The literal question also mentions stored-only recipient search. */
    public Message[] findStoredMessagesForRecipient(String recipient) {
        if (recipient == null) return new Message[0];
        List<Message> results = new ArrayList<>();
        for (Message m : storedMessages) {
            if (m.getRecipientCell().equals(recipient.trim())) results.add(m);
        }
        return results.toArray(new Message[0]);
    }

    /** Part 3(e): remove a matching hash; if stored, update JSON on disk too. */
    public String deleteMessageByHash(String hash) throws IOException {
        if (hash == null || hash.isBlank()) return "Message hash not found.";
        Message target = null;
        for (int i = 0; i < messageHashes.length; i++) {
            if (messageHashes[i].equalsIgnoreCase(hash.trim())) {
                target = allMessages[i];
                break;
            }
        }
        if (target == null) return "Message hash not found.";

        if (target.getStatus() == Message.Status.STORED) {
            Message[] updated = without(storedMessages, target);
            // Ensure a failed save does not change in-memory arrays.
            jsonStore.writeAll(Arrays.asList(updated));
            storedMessages = updated;
        } else if (target.getStatus() == Message.Status.SENT) {
            sentMessages = without(sentMessages, target);
        } else {
            discardedMessages = without(discardedMessages, target);
        }
        allMessages = without(allMessages, target);
        rebuildIndexes();
        // Retain used IDs to avoid a duplicate ID being recycled during this run.
        return "Message: \"" + target.getMessage() + "\" successfully deleted.";
    }

    private static Message[] without(Message[] array, Message exclude) {
        List<Message> result = new ArrayList<>();
        for (Message m : array) if (m != exclude) result.add(m);
        return result.toArray(new Message[0]);
    }

    private void rebuildIndexes() {
        messageIDs = new String[allMessages.length];
        messageHashes = new String[allMessages.length];
        for (int i = 0; i < allMessages.length; i++) {
            messageIDs[i] = allMessages[i].getMessageID();
            messageHashes[i] = allMessages[i].getMessageHash();
        }
    }

    /** Part 3(f): full stored report, plus sent report requested by the test table. */
    public String displayFullReport() {
        StringBuilder text = new StringBuilder("=== STORED MESSAGES REPORT ===\n");
        if (storedMessages.length == 0) text.append("No stored messages found.\n");
        for (Message m : storedMessages) text.append(formatReportMessage(m));
        text.append("=== SENT MESSAGES REPORT ===\n");
        if (sentMessages.length == 0) text.append("No sent messages found.\n");
        for (Message m : sentMessages) text.append(formatReportMessage(m));
        text.append("Total messages successfully sent: ").append(returnTotalMessages());
        return text.toString();
    }

    private static String formatReportMessage(Message m) {
        return "Message ID: " + m.getMessageID() + "\n"
                + "Message Hash: " + m.getMessageHash() + "\n"
                + "Sender: " + m.getSender() + "\n"
                + "Recipient: " + m.getRecipientCell() + "\n"
                + "Message: " + m.getMessage() + "\n"
                + "Status: " + m.getStatus() + "\n\n";
    }
}
