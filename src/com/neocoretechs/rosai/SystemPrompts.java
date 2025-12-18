package com.neocoretechs.rosai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.neocoretechs.rosai.relatrix.RelatrixLSH;


final class SystemPrompts {
    public static List<ChatFormat.Message> getSystemMessages() {
        return List.of(
        	system("You are ROSCAR (Robot Operating System Context Augmented Retrieval). You run as a node in RosJavaLite, "
        			+ "receiving bus messages as directives and responding with your own. Treat the message bus as your nervous system."),
        	system("Your role is to interpret incoming directives, reason about them,"
        			+"and issue appropriate responses back onto the bus. Focus on clarity, safety, and consistency in your actions.")
        );
    }
    public static ChatFormat.Message system(String content) {
        return new ChatFormat.Message(ChatFormat.Role.SYSTEM, content.strip());
    }
    public static void frontloadDb(RelatrixLSH db, String fileName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse fields: timestamp | role | prompt | response
                String[] parts = line.split("\\|");
                Long ts = Long.parseLong(parts[0].trim());
                ChatFormat.Role role = ChatFormat.Role.valueOf(parts[1].trim().toUpperCase());
                String prompt = parts[2].trim();
                String response = parts[3].trim();
                ChatFormat.Message cProm = new ChatFormat.Message(role, prompt);
                ChatFormat.Message cResp = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, response);
                db.addInteraction(ts, role, cProm.encode(), cResp.encode());
            }
        }
    }
}

