package com.mycompany.registration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Part 2 writes stored messages to JSON; Part 3 reads them back into an array.
 * This small JSON reader handles the string and integer fields in our file format.
 * It does not depend on an external JSON library and rejects malformed input.
 */
public final class JsonMessageStore {
    private final Path path;

    public JsonMessageStore(Path path) {
        if (path == null) throw new IllegalArgumentException("Storage path is required.");
        this.path = path.toAbsolutePath();
    }

    public Path getPath() { return path; }

    /** Read an existing messages.json file; an absent file is an empty stored array. */
    public List<Message> readAll() throws IOException {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            // A directory cannot be a JSON file. Allow starting the app, then
            // report the error if a user actually tries to store a message.
            return new ArrayList<>();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        List<Map<String, Object>> records = new Parser(json).readRecords();
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> record : records) {
            String id = stringField(record, "messageID", null);
            String recipient = stringField(record, "recipientCell", null);
            String content = stringField(record, "message", null);
            String hash = stringField(record, "messageHash", null);
            String sender = stringField(record, "sender", "Not recorded");
            int index = integerField(record, "sequenceNumber", sequenceFromHash(hash));
            try {
                Message restored = Message.restoreStored(sender, recipient, content, index, id, hash);
                messages.add(restored);
            } catch (IllegalArgumentException badRecord) {
                throw new IOException("Invalid stored message: " + badRecord.getMessage(), badRecord);
            }
        }
        return messages;
    }

    /** Compatibility with the Part 2 storage interface. */
    public void append(Message message) throws IOException {
        List<Message> current = readAll();
        for (Message existing : current) {
            if (existing.getMessageID().equals(message.getMessageID())) {
                throw new IOException("A stored message already has that ID.");
            }
        }
        current.add(message);
        writeAll(current);
    }

    /** Replace the file atomically, including when a stored message was deleted. */
    public void writeAll(List<Message> messages) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) json.append(",\n");
            Message m = messages.get(i);
            json.append("  {\n")
                .append("    \"messageID\": \"").append(escape(m.getMessageID())).append("\",\n")
                .append("    \"sender\": \"").append(escape(m.getSender())).append("\",\n")
                .append("    \"recipientCell\": \"").append(escape(m.getRecipientCell())).append("\",\n")
                .append("    \"messageLength\": ").append(m.getMessageLength()).append(",\n")
                .append("    \"sequenceNumber\": ").append(m.getSequenceNumber()).append(",\n")
                .append("    \"messageHash\": \"").append(escape(m.getMessageHash())).append("\",\n")
                .append("    \"message\": \"").append(escape(m.getMessage())).append("\"\n")
                .append("  }");
        }
        json.append("\n]\n");
        Path tmp = Files.createTempFile(parent, "quickchat-", ".tmp");
        try {
            Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String stringField(Map<String, Object> record, String key, String otherwise)
            throws IOException {
        Object value = record.get(key);
        if (value == null && otherwise != null) return otherwise;
        if (!(value instanceof String)) throw new IOException("JSON string missing: " + key);
        return (String) value;
    }

    private static int integerField(Map<String, Object> record, String key, int otherwise)
            throws IOException {
        Object value = record.get(key);
        if (value == null) return otherwise;
        if (!(value instanceof Integer)) throw new IOException("Invalid integer field: " + key);
        return (Integer) value;
    }

    /** Older Part 2 files did not save sequenceNumber, but do have it in the hash. */
    private static int sequenceFromHash(String hash) throws IOException {
        if (hash == null) return 0;
        String[] parts = hash.split(":", 3);
        if (parts.length < 3) throw new IOException("Malformed stored message hash.");
        try { return Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { throw new IOException("Malformed message counter in hash.", e); }
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                default:
                    if (c < 0x20) result.append(String.format("\\u%04x", (int)c));
                    else result.append(c);
            }
        }
        return result.toString();
    }

    /** Basic strict parser for arrays of objects with string and integer properties. */
    private static final class Parser {
        private final String data;
        private int cursor;

        Parser(String data) { this.data = data; }

        List<Map<String, Object>> readRecords() throws IOException {
            List<Map<String, Object>> list = new ArrayList<>();
            expect('[');
            if (consume(']')) { end(); return list; }
            do {
                list.add(readObject());
                if (consume(']')) { end(); return list; }
                expect(',');
            } while (true);
        }

        private Map<String, Object> readObject() throws IOException {
            Map<String, Object> object = new LinkedHashMap<>();
            expect('{');
            if (consume('}')) return object;
            do {
                String key = readString();
                expect(':');
                skipSpace();
                if (cursor >= data.length()) throw invalid("Expected value");
                Object value;
                if (data.charAt(cursor) == '"') value = readString();
                else value = readInt();
                if (object.putIfAbsent(key, value) != null) throw invalid("Duplicate field: " + key);
                if (consume('}')) return object;
                expect(',');
            } while (true);
        }

        private int readInt() throws IOException {
            skipSpace();
            int start = cursor;
            if (cursor < data.length() && data.charAt(cursor) == '-') cursor++;
            while (cursor < data.length() && Character.isDigit(data.charAt(cursor))) cursor++;
            if (cursor == start) throw invalid("Expected integer");
            try { return Integer.parseInt(data.substring(start, cursor)); }
            catch (NumberFormatException e) { throw invalid("Invalid integer"); }
        }

        private String readString() throws IOException {
            expect('"');
            StringBuilder text = new StringBuilder();
            while (cursor < data.length()) {
                char c = data.charAt(cursor++);
                if (c == '"') return text.toString();
                if (c < 0x20) throw invalid("Unescaped control character");
                if (c != '\\') { text.append(c); continue; }
                if (cursor >= data.length()) throw invalid("Unfinished escape");
                char escaped = data.charAt(cursor++);
                switch (escaped) {
                    case '"': text.append('"'); break;
                    case '\\': text.append('\\'); break;
                    case '/': text.append('/'); break;
                    case 'n': text.append('\n'); break;
                    case 'r': text.append('\r'); break;
                    case 't': text.append('\t'); break;
                    case 'b': text.append('\b'); break;
                    case 'f': text.append('\f'); break;
                    case 'u':
                        if (cursor + 4 > data.length()) throw invalid("Unfinished Unicode escape");
                        try { text.append((char) Integer.parseInt(data.substring(cursor, cursor + 4), 16)); }
                        catch (NumberFormatException e) { throw invalid("Invalid Unicode escape"); }
                        cursor += 4;
                        break;
                    default: throw invalid("Unsupported escape");
                }
            }
            throw invalid("Unterminated string");
        }

        private boolean consume(char wanted) {
            skipSpace();
            if (cursor < data.length() && data.charAt(cursor) == wanted) { cursor++; return true; }
            return false;
        }
        private void expect(char expected) throws IOException {
            if (!consume(expected)) throw invalid("Expected '" + expected + "'");
        }
        private void end() throws IOException {
            skipSpace();
            if (cursor != data.length()) throw invalid("Unexpected trailing content");
        }
        private void skipSpace() {
            while (cursor < data.length() && Character.isWhitespace(data.charAt(cursor))) cursor++;
        }
        private IOException invalid(String reason) {
            return new IOException("Invalid messages.json at character " + cursor + ": " + reason);
        }
    }
}
