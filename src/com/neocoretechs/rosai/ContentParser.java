package com.neocoretechs.rosai;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
/**
 * Extract content from either URL or file by performing xpath via Jsoup.
 * Extract the specific xpath designators from the RosJavaLite ParmeterTree for the task at hand.
 * @author Jontahan Groff Copyright(C) NeoCoreTechs 2025
 */
public class ContentParser {
	static Stack<String> stack = new Stack<String>();
	static ArrayList<String> visited = new ArrayList<String>();
	/**
	 * Parse the content for file and xpath directive.
	 * @param f File root
	 * @param xPath xpath directive
	 * @return The String content that matches directive
	 */
	private static String parseFile(File f, String xPath) throws Exception {
		Document doc = Jsoup.parse(f, "UTF-8", f.toURI().toString());
		Elements results = null;
		results = doc.selectXpath(xPath);
		StringBuilder sb = new StringBuilder();
		for (Element e : results) {
		    String t = e.text().trim();
		    if (!t.isEmpty()) {
		        sb.append(t).append("\n");
		    }
		}
		return sb.toString();
	}
	/**
	 * Parse the content for url and xpath directive.
	 * Impersonate user agents Mozilla, Crapple, Chrome
	 * @param urlc link root 
	 * @param xPath xpath directive
	 * @return The String content
	 */
	private static String parseUrl(String urlc, String xPath) throws Exception {
		Document doc = Jsoup.connect(urlc)
				.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
				.get();
		Elements results = null;
		results = doc.selectXpath(xPath);
		StringBuilder sb = new StringBuilder();
		for (Element e : results) {
		    String t = e.text().trim();
		    if (!t.isEmpty()) {
		        sb.append(t).append("\n");
		    }
		}
		return sb.toString();
	}
	/**
	 * Build the visited array and stack for parsed links
	 * @param f the file
	 * @throws IOException
	 */
	private static void parseLinks(File f) throws IOException {
		Document doc = Jsoup.parse(f, "UTF-8", f.toURI().toString());
		Elements links = doc.select("a[href]");
		for (Element link : links) {
		    String abs = link.attr("abs:href");
		    if (abs == null || abs.isEmpty()) continue;
		    // Only follow HTML pages
		    if (abs.contains("?") || !abs.endsWith(".html")) continue;
		    // Avoid reprocessing
		    if(!visited.contains(abs)) {
		    	visited.add(abs);
		        stack.push(abs);
		    }
		}
	}
	/**
	 * Build the visited array and stack for parsed links
	 * @param urlc the url
	 * @throws IOException
	 */
	private static void parseLinks(String urlc) throws IOException {
		Document doc = Jsoup.connect(urlc)
				.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
				.get();
		Elements links = doc.select("a[href]");
		for (Element link : links) {
		    String abs = link.attr("abs:href");
		    if (abs == null || abs.isEmpty()) continue;
		    // Only follow HTML pages
		    if (abs.contains("?") || !abs.endsWith(".html")) continue;
		    // Avoid reprocessing
		    if(!visited.contains(abs)) {
		    	visited.add(abs);
		        stack.push(abs);
		    }
		}
	}
	public static String extractAllContent() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String file = stack.pop();
		    System.out.println(">>> parsing file from link: " + file);
		    String fileTrim;
		    if (file.startsWith("file:/"))
		        fileTrim = file.substring(6);
		    else
		        fileTrim = file;
		    File f = new File(fileTrim);
		    // Extract real content
		    String desc = parseFile(f, "//div[@class='description']//div[@class='block']");
		    String methods = parseFile(f, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parseFile(f, "//div[@class='details']//div[contains(@class,'block')]");
		    if(desc != null && desc.trim().length() > 0)
		    	sb.append("DESCRIPTION:\n" + desc);
		    if(methods != null && methods.trim().length() > 0)
		    	sb.append("METHOD SUMMARY:\n" + methods);
		    if(details != null && details.trim().length() > 0)
		    	sb.append("DETAILS:\n" + details);
		    // Continue traversal
		    parseLinks(f);
		}
		return sb.toString();
	}
	
	public static String extractAllContentUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String url = stack.pop();
		    System.out.println(">>> parsing file from url: " + url);
		    // Extract real content
		    String desc = parseUrl(url, "//div[@class='description']//div[@class='block']");
		    String methods = parseUrl(url, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parseUrl(url, "//div[@class='details']//div[contains(@class,'block')]");
		    if(desc != null && desc.trim().length() > 0)
		    	sb.append("DESCRIPTION:\n" + desc);
		    if(methods != null && methods.trim().length() > 0)
		    	sb.append("METHOD SUMMARY:\n" + methods);
		    if(details != null && details.trim().length() > 0)
		    	sb.append("DETAILS:\n" + details);
		    // Continue traversal
		    parseLinks(url);
		}
		return sb.toString();
	}
	public static String extractAllStructure() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String file = stack.pop();
		    String fileTrim;
			System.out.println(">>>parsing file from link:"+file);
			if(file.startsWith("file:/"))
				fileTrim = file.substring(6);
			else
				fileTrim = file;
			File f = new File(fileTrim);
		    String s = ContentParser.parseFile(f,"//a");
			if(s != null)
				sb.append(s);
			else
				System.out.println("no text for "+f);
		}
		return sb.toString();
	}
	public static String extractAllStructureUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String url = stack.pop();
			System.out.println(">>>parsing url from link:"+url);
		    String s = ContentParser.parseUrl(url,"//a");
			if(s != null)
				sb.append(s);
			else
				System.out.println("no text for "+url);
		}
		return sb.toString();
	}
	public static void main(String[] args) throws Exception {
		if(args[0].startsWith("http")) {
			String f = args[0];
			String s = ContentParser.parseUrl(f,"//a");
			if(s != null)
				System.out.println(s);
			else
				System.out.println("no text for "+f);
			parseLinks(f);
			System.out.println(extractAllStructureUrl());
			visited.clear();
			parseLinks(f);
			System.out.println(extractAllContentUrl());
		} else {
			// extract file content
			String file = args[0];
			String fileTrim;
			if(file.startsWith("file://"))
				fileTrim = file.substring(7);
			else
				fileTrim = file;
			File f = new File(fileTrim);
			System.out.println(">>>parsing file:"+fileTrim);
			String s = ContentParser.parseFile(f,"//a");
			if(s != null)
				System.out.println(s);
			else
				System.out.println("no text for "+f);
			parseLinks(f);
			System.out.println(extractAllStructure());
			visited.clear();
			parseLinks(f);
			System.out.println(extractAllContent());
		}
	}
}
