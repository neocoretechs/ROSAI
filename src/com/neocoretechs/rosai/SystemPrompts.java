package com.neocoretechs.rosai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

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
     * JSONArray(0) = desc.put("title",string[(0,1)); <br>
	 * desc.put("Xpath", string(0,2));
	 * @see RelatrixLSH
     * @see ChatFormat
     * @see TreeManager#setupParser(String[][])
     * @param db The RelatrixLSH db
     * @param chatFormat ChatFormat instance
     * @param f The html file
     * @throws IOException
     */
    public static void frontloadDbFromHtml(RelatrixLSH db, ChatFormat chatFormat, File f) throws Exception {
    	//JSONArray ja = new JSONArray();
    	//for(int i = 0; i < xPath.length; i++) {
    	//	JSONObject desc = new JSONObject();
    	//	desc.put("title",xPath[i][1]);
    	//	desc.put("Xpath", xPath[i][2]);
    	//	ja.put(i,desc);
    	//}
    	//TreeManager.getInstance().set("parse", ja.toString());
    	String prompt = ContentParser.extractProgressive(f);
    	Long ts = System.currentTimeMillis();
    	ChatFormat.Role role = ChatFormat.Role.USER;
    	String response = "Content from root node "+f.getName()+" parsed at "+LocalDateTime.now();
    	ChatFormat.Message cProm = new ChatFormat.Message(chatFormat, role, prompt);
    	ChatFormat.Message cResp = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, response);
    	db.addInteraction(ts, role, cProm.encode(), cResp.encode());
    	while ((prompt = ContentParser.extractProgressiveFile()) != null) {
    		if(prompt.isBlank())
    			continue;
    		cProm = new ChatFormat.Message(chatFormat, role, prompt);
    		ts = System.currentTimeMillis();
    		Thread.sleep(1);
    		db.addInteraction(ts, role, cProm.encode(), cResp.encode());
    	}
    }
    /**
     * Assume the xpath parse is set in parametertree format: <p>
     * JSONArray(0) = desc.put("title",string[(0,1)); <br>
	 * desc.put("Xpath", string(0,2));
	 * @see RelatrixLSH
     * @see ChatFormat
     * @see TreeManager#setupParser(String[][])
     * @param db The RelatrixLSH db
     * @param chatFormat ChatFormat instance
     * @param s The html URL
     * @throws IOException
     */
    public static void frontloadDbFromHtml(RelatrixLSH db, ChatFormat chatFormat, String s) throws Exception {
    	//JSONArray ja = new JSONArray();
    	//for(int i = 0; i < xPath.length; i++) {
    	//	JSONObject desc = new JSONObject();
    	//	desc.put("title",xPath[i][1]);
    	//	desc.put("Xpath", xPath[i][2]);
    	//	ja.put(i,desc);
    	//}
    	//TreeManager.getInstance().set("parse", ja.toString());
    	String prompt = ContentParser.extractProgressive(s);
      	Long ts = System.currentTimeMillis();
    	ChatFormat.Role role = ChatFormat.Role.USER;
    	String response = "Content from root node "+s+" parsed at "+LocalDateTime.now();
    	ChatFormat.Message cProm = new ChatFormat.Message(chatFormat, role, prompt);
    	ChatFormat.Message cResp = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, response);
    	db.addInteraction(ts, role, cProm.encode(), cResp.encode());
    	while ((prompt = ContentParser.extractProgressiveUrl()) != null) {
    		if(prompt.isBlank())
    			continue;
    		cProm = new ChatFormat.Message(chatFormat, role, prompt);
    		ts = System.currentTimeMillis();
    		Thread.sleep(1);
    		db.addInteraction(ts, role, cProm.encode(), cResp.encode());
    	}
    }
}

