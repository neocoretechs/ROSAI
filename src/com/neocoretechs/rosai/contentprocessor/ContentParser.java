package com.neocoretechs.rosai.contentprocessor;

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
 * Extract the specific xpath designators from the RosJavaLite ParmeterTree for the task at hand.<p>
 * We divide the extraction into that of structure, or link description, basic //a xpath, and content, which is detailed, divider delimited
 * content specified by more complex xpath designators supplied for whatever type of content we are expecting to parse.<p>
 * @author Jonathan Groff Copyright(C) NeoCoreTechs 2025
 */
public class ContentParser {
	static Stack<String> stack = new Stack<String>();
	static ArrayList<String> visited = new ArrayList<String>();
	private static String progressiveUrlSource;
	private static File progressiveFileSource;
	private static boolean extractContent = true;
	private static boolean extractStructure = true;
	/**
	 * Parse the content for file and xpath directive.
	 * @param f File root
	 * @param xPath xpath directive
	 * @return The String content that matches directive
	 */
	private static String parse(File f, String xPath) throws Exception {
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
	private static String parse(String urlc, String xPath) throws Exception {
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
	 * Build the array of visited links and link stack for parsed links of file based content.
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
	 * Build the array of visited links and stack for parsed links or url based content.
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
	/**
	 * Traverse the entire structure and build one large String of provided xpath descriptors
	 * by removing top stack entry until its empty. Process entire file based structure with one call.
	 * @return The one monolithic string of entire parsed file based structure
	 * @throws Exception
	 */
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
		    String desc = parse(f, "//div[@class='description']//div[@class='block']");
		    String methods = parse(f, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parse(f, "//div[@class='details']//div[contains(@class,'block')]");
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
	/**
	 * Progressively extract file based content by iteratively traversing stack contents on each call
	 * @return The xpath descriptor group parsed results for top stack entry of file based content
	 * @throws Exception
	 */
	public static String extractProgressiveContent() throws Exception {
		StringBuilder sb = new StringBuilder();
		if(!stack.isEmpty()) {
		    String file = stack.pop();
		    System.out.println(">>> parsing file from link: " + file);
		    String fileTrim;
		    if (file.startsWith("file:/"))
		        fileTrim = file.substring(6);
		    else
		        fileTrim = file;
		    File f = new File(fileTrim);
		    // Extract real content
		    String desc = parse(f, "//div[@class='description']//div[@class='block']");
		    String methods = parse(f, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parse(f, "//div[@class='details']//div[contains(@class,'block')]");
		    if(desc != null && desc.trim().length() > 0)
		    	sb.append("DESCRIPTION:\n" + desc);
		    if(methods != null && methods.trim().length() > 0)
		    	sb.append("METHOD SUMMARY:\n" + methods);
		    if(details != null && details.trim().length() > 0)
		    	sb.append("DETAILS:\n" + details);
		    // Continue traversal
		    parseLinks(f);
		} else
			return null;
		return sb.toString();
	}
	/**
	 * Extract all URL based content by iteratively traversing stack contents in a single call
	 * @return The xpath descriptor group parsed results for all stack entries
	 * @throws Exception
	 */
	public static String extractAllContentUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String url = stack.pop();
		    System.out.println(">>> parsing file from url: " + url);
		    // Extract real content
		    String desc = parse(url, "//div[@class='description']//div[@class='block']");
		    String methods = parse(url, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parse(url, "//div[@class='details']//div[contains(@class,'block')]");
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
	/**
	 * Progressively extract URL based content by iteratively traversing stack contents on each call
	 * @return The xpath descriptor group parsed results for top stack entry. null if stack is empty.
	 * @throws Exception
	 */
	public static String extractProgressiveContentUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		if(!stack.isEmpty()) {
		    String url = stack.pop();
		    System.out.println(">>> parsing file from url: " + url);
		    // Extract real content
		    String desc = parse(url, "//div[@class='description']//div[@class='block']");
		    String methods = parse(url, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
		    String details = parse(url, "//div[@class='details']//div[contains(@class,'block')]");
		    if(desc != null && desc.trim().length() > 0)
		    	sb.append("DESCRIPTION:\n" + desc);
		    if(methods != null && methods.trim().length() > 0)
		    	sb.append("METHOD SUMMARY:\n" + methods);
		    if(details != null && details.trim().length() > 0)
		    	sb.append("DETAILS:\n" + details);
		    // Continue traversal
		    parseLinks(url);
		} else
			return null;
		return sb.toString();
	}
	/**
	 * Extract the entire structure of File based links on the stack by popping stack entries until empty.
	 * By structure we mean top level link descriptions.
	 * @return The entire structure representation of the links in file based document. null if no content.
	 * @throws Exception
	 */
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
		    String s = ContentParser.parse(f,"//a");
			if(s != null)
				sb.append(s);
		}
		return sb.toString();
	}
	/**
	 * Progressively extract file based structure by iteratively traversing stack contents on each call.
	 * Structure is link description vs detailed content.
	 * @return The xpath descriptor group parsed results for top stack entry. null if stack is empty.
	 * @throws Exception
	 */
	public static String extractProgressiveStructure() throws Exception {
		StringBuilder sb = new StringBuilder();
		if(!stack.isEmpty()) {
		    String file = stack.pop();
		    String fileTrim;
			System.out.println(">>>parsing file from link:"+file);
			if(file.startsWith("file:/"))
				fileTrim = file.substring(6);
			else
				fileTrim = file;
			File f = new File(fileTrim);
		    String s = ContentParser.parse(f,"//a");
			if(s != null)
				sb.append(s);
		} else
			return null;
		return sb.toString();
	}
	/**
	 * Extract the entire structure of URL based links on the stack by popping stack entries until empty.
	 * By structure we mean top level link descriptions.
	 * @return The entire structure representation of the links in URL based document.
	 * @throws Exception
	 */
	public static String extractAllStructureUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		while (!stack.isEmpty()) {
		    String url = stack.pop();
			System.out.println(">>>parsing url from link:"+url);
		    String s = ContentParser.parse(url,"//a");
			if(s != null)
				sb.append(s);
		}
		return sb.toString();
	}
	/**
	 * Progressively extract URL based structure by iteratively traversing stack contents on each call.
	 * Structure is link description vs detailed content.
	 * @return The xpath descriptor group parsed results for top stack entry. null if no content or stack is empty;
	 * @throws Exception
	 */
	public static String extractProgressiveStructureUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		if(!stack.isEmpty()) {
		    String url = stack.pop();
			System.out.println(">>>parsing url from link:"+url);
		    String s = ContentParser.parse(url,"//a");
			if(s != null)
				sb.append(s);
		} else
			return null;                                                                                                                                                     
		return sb.toString();
	}
	/**
	 * Starting from the source URL, perform the structure based extraction, then the content based extraction.
	 * Top level call for processing entire source in one pass.
	 * @param source the starting URL
	 * @return The monolithic combination of structure and content. null if no content
	 * @throws Exception
	 */
	public static String extract(String source) throws Exception {
		StringBuilder sb = new StringBuilder();
		String s = ContentParser.parse(source,"//a");
		if(s != null)
			sb.append(s);
		parseLinks(source);
		sb.append(extractAllStructureUrl());
		visited.clear();
		parseLinks(source);
		sb.append(extractAllContentUrl());
		return sb.toString();
	}
	/**
	 * Begin the process of progressively extracting the links, then the structure of the source document.
	 * Top level call for progressive extraction of URL based content.
	 * @param source The source URL
	 * @return null if no content, top level structure otherwise.
	 * @throws Exception
	 */
	public static String extractProgressive(String source) throws Exception {
		StringBuilder sb = new StringBuilder();
		String s = ContentParser.parse(source,"//a");
		if(s != null)
			sb.append(s);
		parseLinks(source);
		progressiveUrlSource = source;
		return sb.toString();
	}
	/**
	 * Continue with progressive extraction after initial call to extractProgressive.
	 * Call repeatedly until null is returned.
	 * @return The next item on the stack of URL based content, parsed.
	 * @throws Exception
	 */
	public static String extractProgressiveUrl() throws Exception {
		StringBuilder sb = new StringBuilder();
		String content = extractProgressiveStructureUrl();
		if(extractStructure) {
			if(content == null) {
				visited.clear();
				parseLinks(progressiveUrlSource);
				extractStructure  = false;
				content = extractProgressiveContentUrl();
				if(content == null)
					return sb.toString();
				sb.append(content);
			}
			return sb.toString();
		}
		content = extractProgressiveContentUrl();
		if(content == null) {
			visited.clear();
			extractStructure = true;
			return null;
		}
		sb.append(content);
		return sb.toString();
	}
	/**
	 * Starting from the source URL, perform the structure based extraction, then the content based extraction.
	 * Top level call for processing entire source in one pass.
	 * @param source the starting File
	 * @return The monolithic combination of structure and content
	 * @throws Exception
	 */
	public static String extract(File source) throws Exception {
		// extract file content
		StringBuilder sb = new StringBuilder();
		String s = ContentParser.parse(source,"//a");
		if(s != null)
			sb.append(s);
		else
			System.out.println("no text for "+source);
		parseLinks(source);
		sb.append(extractAllStructure());
		visited.clear();
		parseLinks(source);
		sb.append(extractAllContent());
		return sb.toString();
	}
	/**
	 * Begin the process of progressively extracting the links, then the structure of the source document.
	 * Top level call for progressive extraction of File based content.
	 * @param source The source File
	 * @return null if no content, top level structure otherwise.
	 * @throws Exception
	 */
	public static String extractProgressive(File source) throws Exception {
		// extract file content
		StringBuilder sb = new StringBuilder();
		String s = ContentParser.parse(source,"//a");
		if(s != null)
			sb.append(s);
		parseLinks(source);
		progressiveFileSource = source;
		return sb.toString();
	}
	public static String extractProgressiveFile() throws Exception {
		StringBuilder sb = new StringBuilder();
		String content = extractProgressiveStructure();
		if(extractStructure) {
			if(content == null) {
				visited.clear();
				parseLinks(progressiveFileSource);
				extractStructure  = false;
				content = extractProgressiveContent();
				if(content == null)
					return sb.toString();
				sb.append(content);
			}
			return sb.toString();
		}
		content = extractProgressiveContent();
		if(content == null) {
			visited.clear();
			extractStructure = true;
			return null;
		}
		sb.append(content);
		return sb.toString();
	}
	public static void main(String[] args) throws Exception {
		if(args[0].startsWith("http")) {
			//System.out.println(extract(args[0]));
			// progressively extract
			String content;
			int item = 0;
			System.out.println(extractProgressive(args[0]));
			while((content = extractProgressiveUrl()) != null)
				System.out.println(++item+".) "+content);
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
			//System.out.println(extract(f));
			String content;
			int item = 0;
			System.out.println(extractProgressive(f));
			while((content = extractProgressiveFile()) != null)
				System.out.println(++item+".) "+content);
		}
	}
}