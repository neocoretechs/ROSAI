package com.neocoretechs.rosai.contentprocessor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Stack;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.ros.node.ConnectedNode;

import com.neocoretechs.rosai.parametertree.TreeManager;
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
			    // Only follow HTML pages
			    if(abs == null || abs.isEmpty() || abs.contains("?") || !abs.endsWith(".html")) continue;
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
			    // Only follow HTML pages
			    if (abs == null || abs.isEmpty() || abs.contains("?") || !abs.endsWith(".html")) continue;
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
		public static String extractAllContent(JSONArray titleXpath) throws Exception {
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
				for(int i = 0; i < titleXpath.length(); i++) {
					JSONObject item = new JSONObject();
					JSONObject titleXpathj = titleXpath.getJSONObject(i);
					String title = titleXpathj.getString("title");
					String xPath = titleXpathj.getString("Xpath");
					String desc = parse(f,xPath);
					sb.append(f.toString());
					sb.append("\r\n");
					sb.append(title);
					sb.append("\r\n");
					sb.append(desc);
					sb.append("\r\n");
					parseLinks(f);
				}
			}
			return sb.toString();
		}
		/**
		 * Progressively extract file based content by iteratively traversing stack contents on each call
		 * @param titleXpath the JSONArray with each title, Xpath designator for content to parse
		 * @return The xpath descriptor group parsed results for top stack entry of file based content in JSON format
		 * @throws Exception
		 */
		public static String extractProgressiveContentJson(JSONArray titleXpath) throws Exception {
			JSONObject ret = new JSONObject();
			if(!stack.isEmpty()) {
			    String file = stack.pop();
			    System.out.println(">>> parsing file from link: " + file);
			    String fileTrim;
			    if (file.startsWith("file:/"))
			        fileTrim = file.substring(6);
			    else
			        fileTrim = file;
			    File f = new File(fileTrim);
			    for(int i = 0; i < titleXpath.length(); i++) {
			    	JSONObject item = new JSONObject();
			    	JSONObject titleXpathj = titleXpath.getJSONObject(i);
			    	String title = titleXpathj.getString("title");
			    	String xPath = titleXpathj.getString("Xpath");
			    	String desc = parse(f,xPath);
			    	item.put("link", f.toString());
			    	item.put("title", title);
			    	item.put("content", desc);
			    	ret.append(String.valueOf(i), item);
			    }
			    // Extract real content
			    //String desc = parse(f, "//div[@class='description']//div[@class='block']");
			    //String methods = parse(f, "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
			    //String details = parse(f, "//div[@class='details']//div[contains(@class,'block')]");
			    //if(desc != null && desc.trim().length() > 0)
			    //	sb.append("DESCRIPTION:\n" + desc);
			    //if(methods != null && methods.trim().length() > 0)
			    //	sb.append("METHOD SUMMARY:\n" + methods);
			    //if(details != null && details.trim().length() > 0)
			    //	sb.append("DETAILS:\n" + details);
			    // Continue traversal
			    parseLinks(f);
			} else
				return null;
			return ret.toString();
		}
		/**
		 * Progressively extract file based content by iteratively traversing stack contents on each call
		 * @param titleXpath the JSONArray with each title, Xpath designator for content to parse
		 * @return The xpath descriptor group parsed results for top stack entry of file based content
		 * @throws Exception
		 */
		public static String extractProgressiveContent(JSONArray titleXpath) throws Exception {
			StringBuilder ret = new StringBuilder();
			if(!stack.isEmpty()) {
			    String file = stack.pop();
			    System.out.println(">>> parsing file from link: " + file);
			    String fileTrim;
			    if (file.startsWith("file:/"))
			        fileTrim = file.substring(6);
			    else
			        fileTrim = file;
			    File f = new File(fileTrim);
			    for(int i = 0; i < titleXpath.length(); i++) {
			    	JSONObject titleXpathj = titleXpath.getJSONObject(i);
			    	String title = titleXpathj.getString("title");
			    	String xPath = titleXpathj.getString("Xpath");
			    	String desc = parse(f,xPath);
			    	ret.append(f.toString());
			    	ret.append("\r\n");
			    	ret.append(title);
			    	ret.append("\r\n");
			    	ret.append(desc);
			    	ret.append("\r\n");
			    }
			    parseLinks(f);
			} else
				return null;
			return ret.toString();
		}
		/**
		 * Extract all URL based content by iteratively traversing stack contents in a single call
		 * @return The xpath descriptor group parsed results for all stack entries
		 * @throws Exception
		 */
		public static String extractAllContentUrl(JSONArray titleXpath) throws Exception {
			StringBuilder sb = new StringBuilder();
			while (!stack.isEmpty()) {
			    String url = stack.pop();
			    System.out.println(">>> parsing file from url: " + url);
			    // Extract real content
			    for(int i = 0; i < titleXpath.length(); i++) {
			    	JSONObject titleXpathj = titleXpath.getJSONObject(i);
			    	String title = titleXpathj.getString("title");
			    	String xPath = titleXpathj.getString("Xpath");
			    	String desc = parse(url,xPath);
			    	sb.append(url.toString());
			    	sb.append("\r\n");
			    	sb.append(title);
			    	sb.append("\r\n");
			    	sb.append(desc);
			    	sb.append("\r\n");
			    }
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
		public static String extractProgressiveContentUrl(JSONArray titleXpath) throws Exception {
			StringBuilder sb = new StringBuilder();
			if(!stack.isEmpty()) {
			    String url = stack.pop();
			    System.out.println(">>> parsing file from url: " + url);
			    // Extract real content
			    for(int i = 0; i < titleXpath.length(); i++) {
			    	JSONObject titleXpathj = titleXpath.getJSONObject(i);
			    	String title = titleXpathj.getString("title");
			    	String xPath = titleXpathj.getString("Xpath");
			    	String desc = parse(url,xPath);
			    	sb.append(url.toString());
			    	sb.append("\r\n");
			    	sb.append(title);
			    	sb.append("\r\n");
			    	sb.append(desc);
			    	sb.append("\r\n");
			    }
			    // Continue traversal
			    parseLinks(url);
			} else
				return null;
			return sb.toString();
		}
		/**
		 * Progressively extract URL based content by iteratively traversing stack contents on each call
		 * @return The xpath descriptor group parsed results for top stack entry in JSON format. null if stack is empty.
		 * @throws Exception
		 */
		public static String extractProgressiveContentUrlJson(JSONArray titleXpath) throws Exception {
			JSONObject ret = new JSONObject();
			if(!stack.isEmpty()) {
			    String url = stack.pop();
			    System.out.println(">>> parsing url from: " + url);
			    for(int i = 0; i < titleXpath.length(); i++) {
			    	JSONObject item = new JSONObject();
			    	JSONObject titleXpathj = titleXpath.getJSONObject(i);
			    	String title = titleXpathj.getString("title");
			    	String xPath = titleXpathj.getString("Xpath");
			    	String desc = parse(url,xPath);
			    	item.put("link", url);
			    	item.put("title", title);
			    	item.put("content", desc);
			    	ret.append(String.valueOf(i), item);
			    }
			    parseLinks(url);
			} else
				return null;
			return ret.toString();
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
			String xpathString = (String) TreeManager.getInstance().getOrDefault("parse", "");
			JSONArray titleXpath = new JSONArray(xpathString);
			StringBuilder sb = new StringBuilder();
			String s = ContentParser.parse(source,"//a");
			if(s != null)
				sb.append(s);
			parseLinks(source);
			sb.append(extractAllStructureUrl());
			visited.clear();
			parseLinks(source);
			sb.append(extractAllContentUrl(titleXpath));
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
			String xpathString = (String) TreeManager.getInstance().getOrDefault("parse", "");
			JSONArray titleXpath = new JSONArray(xpathString);
			StringBuilder sb = new StringBuilder();
			String content = extractProgressiveStructureUrl();
			if(extractStructure) {
				if(content == null) {
					visited.clear();
					parseLinks(progressiveUrlSource);
					extractStructure  = false;
					content = extractProgressiveContentUrl(titleXpath);
					if(content == null)
						return sb.toString();
					sb.append(content);
				}
				return sb.toString();
			}
			content = extractProgressiveContentUrl(titleXpath);
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
			String xpathString = (String) TreeManager.getInstance().getOrDefault("parse", "");
			JSONArray titleXpath = new JSONArray(xpathString);
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
			sb.append(extractAllContent(titleXpath));
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
		/**
		 * Continue with progressive extraction after initial call to extractProgressive.
		 * Call repeatedly until null is returned.
		 * @return The next item on the stack of File based content, parsed.
		 * @throws Exception
		 */
		public static String extractProgressiveFileJson() throws Exception {
			String xpathString = (String) TreeManager.getInstance().getOrDefault("parse", "");
			JSONArray titleXpath = new JSONArray(xpathString);
			StringBuilder sb = new StringBuilder();
			String content = extractProgressiveStructure();
			if(extractStructure) {
				if(content == null) {
					visited.clear();
					parseLinks(progressiveFileSource);
					extractStructure  = false;
					content = extractProgressiveContentJson(titleXpath);
					if(content == null)
						return sb.toString();
					sb.append(content);
				}
				return sb.toString();
			}
			content = extractProgressiveContentJson(titleXpath);
			if(content == null) {
				visited.clear();
				extractStructure = true;
				return null;
			}
			sb.append(content);
			return sb.toString();
		}
		
		/**
		 * Continue with progressive extraction after initial call to extractProgressive.
		 * Call repeatedly until null is returned.
		 * @return The next item on the stack of File based content, parsed.
		 * @throws Exception
		 */
		public static String extractProgressiveFile() throws Exception {
			String xpathString = (String) TreeManager.getInstance().getOrDefault("parse", "");
			JSONArray titleXpath = new JSONArray(xpathString);
			StringBuilder sb = new StringBuilder();
			String content = extractProgressiveStructure();
			if(extractStructure) {
				if(content == null) {
					visited.clear();
					parseLinks(progressiveFileSource);
					extractStructure  = false;
					content = extractProgressiveContent(titleXpath);
					if(content == null)
						return sb.toString();
					sb.append(content);
				}
				return sb.toString();
			}
			content = extractProgressiveContent(titleXpath);
			if(content == null) {
				visited.clear();
				extractStructure = true;
				return null;
			}
			sb.append(content);
			return sb.toString();
		}
		
		public static Optional<String> parseHttps(String chatMessage) {
			URI baseURI = URI.create(chatMessage);
			HttpClient client = HttpClient.newBuilder()
			    .followRedirects(HttpClient.Redirect.NORMAL)
			    .connectTimeout(Duration.ofSeconds(20))
			    .build();
			HttpRequest req = HttpRequest.newBuilder()
			    .uri(URI.create(baseURI.toString()))
			    .timeout(Duration.ofSeconds(30))
			    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
			    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
			    .header("Accept-Language", "en-US,en;q=0.9")
			    .header("Accept-Encoding", "gzip, deflate, br")
			    .GET()
			    .build();
			HttpResponse<byte[]> resp;
			try {
				resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
			} catch (IOException | InterruptedException e) {
				return Optional.of("processing URL "+chatMessage+" failed due to :"+e.getMessage()+" at "+ LocalDateTime.now());
			}
			int status = resp.statusCode();
			byte[] bodyBytes = resp.body();
			// If compressed, HttpClient already handled it when using BodyHandlers.ofString in many cases.
			// Convert bytes to string using correct charset if available
			String charset = "UTF-8";
			String ct = resp.headers().firstValue("Content-Type").orElse("");
			String contentEncoding = resp.headers().firstValue("Content-Encoding").orElse("").toLowerCase();
			if (ct.contains("charset=")) charset = ct.substring(ct.indexOf("charset=") + 8).trim();
			/*String html;
			//html = new String(bodyBytes, charset);
			CharsetDecoder decoder = Charset.forName(charset).newDecoder();    
			//Error handling actions for malformed and unmappable characters
			decoder.onMalformedInput(CodingErrorAction.REPLACE);
			decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
			//I/O buffer creation
			CharBuffer charStore = CharBuffer.allocate(bodyBytes.length);
			ByteBuffer utfStore = ByteBuffer.wrap(bodyBytes);
			//Output string instance to concatenate each decoded byte
			StringBuilder decodedText = new StringBuilder();
			CoderResult result;
			do {
				result = decoder.decode(utfStore, charStore, false);
				charStore.flip();
				decodedText.append(charStore);
				charStore.clear(); 
				if (result.isError()) {
					// Error handling logic
					if (result.isMalformed()) {
						return Optional.of("Encountered malformed byte sequence!");//Malformed error
					} else if (result.isUnmappable()) {
						return Optional.of("Encountered unmappable character!");//Unmappable error
					}
				}     
			} while (!result.isUnderflow());     
			System.out.println("Decoded Text: " + decodedText); // Decoded text is shown as output!
			*/
			System.out.println("Response="+resp.statusCode());
			System.out.println("Content type:"+ct+" encoding:"+contentEncoding+" charset:"+charset);
			if(status >= 200 && status <= 299) {
				try {
					Document d = ContentParser.fetchAndParse(ct, contentEncoding, bodyBytes, baseURI.toString());//Jsoup.parse(decodedText.toString(),baseURI.toString());
					chatMessage = d.text();
				} catch (Exception e) {
					return Optional.of("processing URL "+chatMessage+" failed due to :"+e.getMessage()+" at "+ LocalDateTime.now());
				}
			} else {
				return Optional.of("processing URL "+chatMessage+" failed due to response:"+resp.statusCode()+" at "+ LocalDateTime.now());
			}
			return Optional.of(chatMessage);
		}
		
		public static void unitTest(String source, ConnectedNode cn) throws Exception {
			TreeManager.getInstance().init(cn);
			if(source.startsWith("http")) {
				//System.out.println(extract(args[0]));
				// progressively extract
				String content;
				int item = 0;
				System.out.println(extractProgressive(source));
				while((content = extractProgressiveUrl()) != null)
					System.out.println(++item+".) "+content);
			} else {
				// extract file content
				String file = source;
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
			    // Extract real content
				// JSONArray of JSONObjects title, xpath
				JSONArray ja = new JSONArray();
				JSONObject desc = new JSONObject();
				desc.put("title","DESCRIPTION");
			    desc.put("Xpath", "//div[@class='description']//div[@class='block']");
			    ja.put(0,desc);
				JSONObject meth = new JSONObject();
				meth.put("title","METHOD SUMMARY");
			    meth.put("Xpath", "//table[contains(@class,'memberSummary')]//td[@class='colLast']");
			    ja.put(1,meth);
				JSONObject deets = new JSONObject();
				deets.put("title","DETAILS");
			    deets.put("Xpath", "//div[@class='details']//div[contains(@class,'block')]");
			    ja.put(2,deets);    
			    TreeManager.getInstance().set("parse", ja.toString());
				while((content = extractProgressiveFile()) != null)
					System.out.println(++item+".) "+content);
			}
		}

		public static Document fetchAndParse(String contentType, String contentEncoding, byte[] bodyBytes, String baseUri) throws Exception {
		    //HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
		    //byte[] bodyBytes = resp.body();
		    // 1) Inspect headers
		    //String contentEncoding = resp.headers().firstValue("Content-Encoding").orElse("").toLowerCase();
		    //String contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase();
		    // 2) Decompress if needed
		    InputStream in = new ByteArrayInputStream(bodyBytes);
		    //if (contentEncoding.contains("br")) {
		       // in = new BrotliInputStream(in);
		    //} else 
		    if (contentEncoding.contains("gzip")) {
		        in = new GZIPInputStream(in);
		    } else if (contentEncoding.contains("deflate")) {
		        in = new java.util.zip.InflaterInputStream(in);
		    } // else no decompression

		    // 3) Determine charset (prefer header; Jsoup can also detect from meta)
		    String charset = null;
		    int idx = contentType.indexOf("charset=");
		    if (idx != -1) {
		        charset = contentType.substring(idx + 8).trim();
		    }
		    // 4) Let Jsoup parse from InputStream; pass charset if known, otherwise let Jsoup detect
		    Document doc;
		    if (charset != null && !charset.isEmpty()) {
		        doc = Jsoup.parse(in, charset, baseUri);
		    } else {
		        // Jsoup will try to detect charset from meta tags
		        doc = Jsoup.parse(in, null, baseUri);
		    }
		    return doc;
		}

}