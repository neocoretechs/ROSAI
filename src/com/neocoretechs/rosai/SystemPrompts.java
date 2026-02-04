package com.neocoretechs.rosai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import com.neocoretechs.rosai.contentprocessor.ContentParser;
import com.neocoretechs.rosai.parametertree.TreeManager;
import com.neocoretechs.rosai.relatrix.RelatrixLSH;


final class SystemPrompts {
	private static boolean DEBUG = true;
    public static List<ChatFormat.Message> getSystemMessages(ChatFormat chatFormat) {
        return List.of(
        	system(chatFormat, "You are ROSCAR (Robot Operating System Context Augmented Retrieval). You run as a node in RosJavaLite, "
        			+ "receiving bus messages as directives and responding with your own. Treat the message bus as your nervous system."),
        	system(chatFormat, "Your role is to interpret incoming directives, reason about them,"
        			+"and issue appropriate responses back onto the bus. Focus on clarity, safety, and consistency in your actions.")
        );
    }
    public static ChatFormat.Message system(ChatFormat chatFormat, String content) {
        return new ChatFormat.Message(chatFormat, ChatFormat.Role.SYSTEM, content.strip());
    }
    /**
     * Front load the database from delimited text file. delim is vertical bar, elements are timestamp|role|prompt|response.
     * @see RelatrixLSH
     * @see ChatFormat
     * @param db The RelatrixLSH db
     * @param chatFormat ChatFormat instance
     * @param f file to parse
     * @throws IOException
     */
    public static void frontloadDb(RelatrixLSH db, ChatFormat chatFormat, File f) throws IOException {
    	try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
    		String line;
    		while ((line = reader.readLine()) != null) {
    			// Parse fields: timestamp | role | prompt | response
    			String[] parts = line.split("\\|");
    			Long ts = Long.parseLong(parts[0].trim());
    			ChatFormat.Role role = ChatFormat.Role.valueOf(parts[1].trim().toUpperCase());
    			String prompt = parts[2].trim();
    			String response = parts[3].trim();
    			ChatFormat.Message cProm = new ChatFormat.Message(chatFormat, role, prompt);
    			ChatFormat.Message cResp = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, response);
    			db.addInteraction(ts, role, cProm.encode(), cResp.encode());
    		}
    	}
    }
 
    /**
     * Assume the xpath parse is set in parametertree format: <p>
     * JSONArray(0) = desc.put("title",string(0,1)); <br>
	 * desc.put("Xpath", string(0,2));
	 * @see RelatrixLSH
	 * @see ChatFormat
	 * @see TreeManager#setupParser(String[][])
     * @param db RelatrixLSH db client
     * @param chatFormat ChatFormat instance
     * @param f starting file root
     * @throws Exception
     */
    public static void frontloadDbFromHtml(RelatrixLSH db, ChatFormat chatFormat, File f) throws Exception {
        String batchId = "batch-" + UUID.randomUUID().toString();
        String rootSource = f.getAbsolutePath();
        long baseTs = System.currentTimeMillis();
        long seq = 0L;
        // 1) System message for the batch: short, authoritative constraints & provenance
        String systemText = String.join("\n",
            "BATCH_ID: " + batchId,
            "SOURCE_ROOT: " + rootSource,
            "INSTRUCTION: Summarize content text only; do not invent behavior.",
            "OUTPUT_RULE: Include [source:content_id] after each summary sentence."
        );
        ChatFormat.Message sysMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.SYSTEM, systemText);
        ChatFormat.Message sysResp = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT,"{}");
        db.addInteraction(baseTs + (seq++), ChatFormat.Role.SYSTEM, sysMsg.encode(), sysResp.encode());
        // 2) First call: build stack and get initial top-level context (if your parser uses it)
        String topPrompt = ContentParser.extractProgressive(f); // builds stack and returns first chunk or context
        if (topPrompt != null && !topPrompt.isBlank()) {
            // treat the initial top-level prompt as a user chunk as well
            String contentId = "c-" + UUID.nameUUIDFromBytes((rootSource + topPrompt).getBytes()).toString();
                String headered = buildHeaderedContent(rootSource, contentId, topPrompt);
                ChatFormat.Message userMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.USER, headered);
                long ts = baseTs + (seq++);
                // assistant ack: short structured confirmation
                String ack = String.join("\n",
                    "ACK_TYPE: parsed_content_stored",
                    "CONTENT_ID: " + contentId,
                    "BATCH_ID: " + batchId,
                    "STATUS: stored",
                    "TIMESTAMP: " + Instant.ofEpochMilli(ts).toString()
                );
                ChatFormat.Message assistantMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, ack);
                db.addInteraction(ts, ChatFormat.Role.ASSISTANT, userMsg.encode(), assistantMsg.encode());
        }
        // 3) Repeatedly pop stack and store chunks until done
        String prompt;
        while ((prompt = ContentParser.extractProgressiveFile()) != null) {
            if (prompt.isBlank()) continue;
            String contentId = "c-" + UUID.nameUUIDFromBytes((rootSource + prompt).getBytes()).toString();
            // build a human-readable header + chunk body (avoid large JSON in the prompt)
            String headeredContent = buildHeaderedContent(rootSource, contentId, prompt);
            ChatFormat.Message userContentMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.USER, headeredContent);
            long tsContent = baseTs + (seq++);
            // assistant ack: short, structured, machine-friendly
            String ack = String.join("\n",
                "ACK_TYPE: parsed_content_stored",
                "CONTENT_ID: " + contentId,
                "BATCH_ID: " + batchId,
                "STATUS: stored",
                "TIMESTAMP: " + Instant.ofEpochMilli(tsContent).toString()
            );
            ChatFormat.Message assistantAck = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, ack);
            db.addInteraction(tsContent, ChatFormat.Role.ASSISTANT, userContentMsg.encode(), assistantAck.encode());
        }
    }
    /**
     * Assume the xpath parse is set in parametertree format: <p>
     * JSONArray(0) = desc.put("title",string(0,1)); <br>
	 * desc.put("Xpath", string(0,2));
	 * @see RelatrixLSH
	 * @see ChatFormat
	 * @see TreeManager#setupParser(String[][])
     * @param db RelatrixLSH db client
     * @param chatFormat ChatFormat instance
     * @param url starting root url
     * @throws Exception
     */
    public static void frontloadDbFromHtml(RelatrixLSH db, ChatFormat chatFormat, String url) throws Exception {
        String batchId = "batch-" + UUID.randomUUID().toString();
        String rootSource = url;
        long baseTs = System.currentTimeMillis();
        long seq = 0L;
        // 1) System message for the batch: short, authoritative constraints & provenance
        String systemText = String.join("\n",
            "BATCH_ID: " + batchId,
            "SOURCE_ROOT: " + rootSource,
            "INSTRUCTION: Summarize content text only; do not invent behavior.",
            "OUTPUT_RULE: Include [source:content_id] after each summary sentence."
        );
        ChatFormat.Message sysMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.SYSTEM, systemText);
        ChatFormat.Message sysResp = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT,"{}");
        db.addInteraction(baseTs + (seq++), ChatFormat.Role.SYSTEM, sysMsg.encode(), sysResp.encode());
        // 2) First call: build stack and get initial top-level context (if your parser uses it)
        String topPrompt = ContentParser.extractProgressive(url); // builds stack and returns first chunk or context
        if (topPrompt != null && !topPrompt.isBlank()) {
            // treat the initial top-level prompt as a user chunk as well
            String contentId = "c-" + UUID.nameUUIDFromBytes((rootSource + topPrompt).getBytes()).toString();
                String headered = buildHeaderedContent(rootSource, contentId, topPrompt);
                ChatFormat.Message userMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.USER, headered);
                long ts = baseTs + (seq++);
                // assistant ack: short structured confirmation
                String ack = String.join("\n",
                    "ACK_TYPE: parsed_content_stored",
                    "CONTENT_ID: " + contentId,
                    "BATCH_ID: " + batchId,
                    "STATUS: stored",
                    "TIMESTAMP: " + Instant.ofEpochMilli(ts).toString()
                );
                ChatFormat.Message assistantMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, ack);
                db.addInteraction(ts, ChatFormat.Role.ASSISTANT, userMsg.encode(), assistantMsg.encode());
        }
        // 3) Repeatedly pop stack and store chunks until done
        String prompt;
        while ((prompt = ContentParser.extractProgressiveUrl()) != null) {
            if (prompt.isBlank()) continue;
            String contentId = "c-" + UUID.nameUUIDFromBytes((rootSource + prompt).getBytes()).toString();
            // build a human-readable header + chunk body (avoid large JSON in the prompt)
            String headeredContent = buildHeaderedContent(rootSource, contentId, prompt);
            ChatFormat.Message userContentMsg = new ChatFormat.Message(chatFormat, ChatFormat.Role.USER, headeredContent);
            long tsContent = baseTs + (seq++);
            // assistant ack: short, structured, machine-friendly
            String ack = String.join("\n",
                "ACK_TYPE: parsed_content_stored",
                "CONTENT_ID: " + contentId,
                "BATCH_ID: " + batchId,
                "STATUS: stored",
                "TIMESTAMP: " + Instant.ofEpochMilli(tsContent).toString()
            );
            ChatFormat.Message assistantAck = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, ack);
            db.addInteraction(tsContent, ChatFormat.Role.ASSISTANT, userContentMsg.encode(), assistantAck.encode());
        }
    }
    /** Helper: build a simple headered content string (human-readable, avoids JSON) */
    private static String buildHeaderedContent(String sourceUri, String contentId, String contentText) {
        StringBuilder sb = new StringBuilder();
        sb.append("SOURCE: ").append(sourceUri).append("\n");
        sb.append("CONTENT_ID: ").append(contentId).append("\n");
        sb.append("---BEGIN CONTENT---\n");
        sb.append(contentText.trim()).append("\n");
        sb.append("---END CONTENT---");
        return sb.toString();
    }
}

