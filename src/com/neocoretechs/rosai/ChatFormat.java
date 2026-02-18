package com.neocoretechs.rosai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility tailored for Llama 3 instruct prompt format.
 */
public class ChatFormat implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(ChatFormat.class);
	public static boolean DEBUG = false;
	private transient Set<Integer> stopTokens;
	private transient int bos;
	public static String endOfTurn = "<|eot_id|>"; // llama3 specific, overwritten in ctor
	
	/* This is the Gemma Role enum:
	public enum Role {
		SYSTEM("model"),
		USER("user"),
		ASSISTANT("model");
		private final String role;
		Role(String role) {
			this.role = role;
		}
		public String getRole() {
			return role;
		}
		@Override
		public String toString() {
			return role;
		}
	}
	*/
	
	// This is the Llama, Mistral etc Role enum
	public enum Role {
		SYSTEM("system"),
		USER("user"),
		ASSISTANT("assistant");
		private final String role;
		Role(String role) {
			this.role = role;
		}
		public String getRole() {
			return role;
		}
		@Override
		public String toString() {
			return role;
		}
	}
	
	public ChatFormat() {
		try {
			int endOfTurnTok = (int) Llama3.getTokenEOTMH.invokeExact();
			int endOfSentenceTok = (int) Llama3.getTokenEOSMH.invokeExact();
			bos = (int) Llama3.getTokenBOSMH.invokeExact();
			//System.out.println("eot="+endOfTurn+" eos="+endOfSentence);
			IntTensor ieot = new IntTensor(new int[] {endOfTurnTok});
			StringTensor out = new StringTensor();
			out.allocate(Llama3.options.getMaxTokens());
			int siz = DeviceManager.tokenToString(ieot, 1, out);
			endOfTurn = out.toString();
			//System.out.println("endOfTurn siz="+siz+" out="+out.toString()+" out len ="+out.toString().length());
			List<Integer> it = DeviceManager.encode(endOfTurn); 
			//int endOfText = it.get(0);
			//int endOfTurn = it.get(1);
			// sanity check
			if(endOfTurnTok != it.get(0) && endOfTurnTok != it.get(1) && endOfSentenceTok != it.get(0) && endOfSentenceTok != it.get(1))
				log.warn("CHAT TEMPLATE STOP TOKEN MISMATCH endOfTurn="+endOfTurnTok+" endOfSentence="+endOfSentenceTok+" encode(<|eot_id|>),int[0]="+it.get(0)+" int[1]="+it.get(1));
			if(endOfTurnTok == endOfSentenceTok)
				++endOfSentenceTok; // keep unique kludge
			stopTokens = Set.of(endOfTurnTok, endOfSentenceTok);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public Set<Integer> getStopTokens() {
		return stopTokens;
	}
	
	public int length(List<Integer> tokens) {
		for(int i = tokens.size()-1; i > 0; i--) {
			if(stopTokens.contains(tokens.get(i)))
					return i;
		}
		return tokens.size();
	}
	/**
	 * Encode list of supplied messages into tokenized List, applying chat templates
	 * @param dialog List of messages to tokenize
	 * @return the tokenized list of templatized messages
	 */
	public List<Integer> encodeDialogPrompt(List<ChatFormat.Message> dialog) {
		//MessageTensor mt = new MessageTensor(dialog);
		//StringTensor st = mt.applyChatTemplate();
		//String resStr = st.toString();
		String resStr = MessageTensor.applyChatTemplate(dialog);
		if(DEBUG)
			log.info("ChatFormat.encodeDialogPrompt="+resStr);
		return DeviceManager.encode(resStr);
	}
	/**
	 * Encode list of supplied messages into dialog text in StringTensor, applying chat templates
	 * @param dialog list of messages to process
	 * @return the StringTensor of templatized messages
	 */
	public StringTensor extractDialogPrompt(List<Message> dialog) {
		//MessageTensor mt = new MessageTensor(dialog);
		StringTensor st = new StringTensor(MessageTensor.applyChatTemplate(dialog));
		if(DEBUG)
			log.info("ChatFormat.extractDialogPrompt="+st.toString());
		return st;
	}
	/**
	 * Strip Llama3 specific chat template formatting producing unformatted string
	 * @param input The input STring
	 * @return the unformatted string
	 
	public String stripFormatting(String input) {
		return input.replaceAll("<\\|.*?\\|>", "")
				.replaceAll("\\*+", "")
				.replaceAll("(?m)^USER:|AI:", "")
				.trim();
	}*/

	public String stripFormatting(String input) {
	    if (input == null || input.isEmpty()) return input;
	    // remove explicit endOfTurn marker (safe for special chars)
	    input = input.replaceAll(Pattern.quote(endOfTurn), "");
	    // remove any <|...|> style tokens (non-greedy, DOTALL so it works across lines)
	    input = input.replaceAll("(?s)<\\|.*?\\|>", "");
	    // build role regex from enum plus alias "AI"
	    String roles = Stream.of(Role.values())
	                         .map(Role::getRole)
	                         .collect(Collectors.joining("|"));
	    // include AI alias
	    String rolesRegex = "(?i)^(?:" + roles + "|AI)\\s*:\\s*"; // (?i) = case-insensitive, ^ with multiline below
	    // remove role labels at start of lines (multiline)
	    input = input.replaceAll("(?m)" + rolesRegex, "");
	    // remove markdown asterisks used for emphasis/bold (one or more)
	    input = input.replaceAll("\\*+", "");
	    // collapse multiple whitespace to single space and trim
	    input = input.replaceAll("\\s{2,}", " ").trim();
	    return input;
	}
	/**
	 * Strip model specific chat template tokens from input
	 * @param input Tokenized input
	 * @return Format stripped tokenized output
	 */
	public List<Integer> stripFormatting(List<Integer> input) {
		List<Integer> res = new ArrayList<Integer>();
		for(Integer in: input) {
			if(!stopTokens.contains(in) && in != bos)
				res.add(in);
		}
		return res;
	}
	/**
	 * Message record. 
	 */
	public record Message(ChatFormat chatFormat, ChatFormat.Role role, String content) implements Serializable {
		@Override
		public String toString() {
			return String.format("[%s] %s", role, content);
		}
		/**
		 * Creates ArrayList of 1, adds 'this' to it, then calls chatFormat.encodeDialogPrompt
		 * which encodes list of supplied messages into tokenized List by applying chat templates,
		 * then extracting the String, then calling DeviceManager.encode to turn it into a list of tokens.
		 * @return The encoded list of tokens
		 */
		public List<Integer> encode() {
			ArrayList<Message> tr = new ArrayList<Message>(1);
			tr.add(this);
			return chatFormat.encodeDialogPrompt(tr);
		}
		/**
		 * Create an internal ArrayList for 'this' singular message, then send it to MessageTensor.applyChatTemplate.
		 * @see MessageTensor#applyChatTemplate
		 * @return The String with chat template applied
		 */
		public String applyChatTemplate() {
			ArrayList<Message> tr = new ArrayList<Message>(1);
			tr.add(this);
			return MessageTensor.applyChatTemplate(tr);
		}
	}
	
	private int[] encodeImpl(Collection<? extends Integer> intc) {
		return intc.stream().mapToInt(i -> i).toArray();
	}

	private static List<String> findAll(Pattern pattern, String text) {
		List<String> allMatches = new ArrayList<>();
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			allMatches.add(matcher.group());
		}
		return allMatches;
	}

	private Map<Pair<Integer, Integer>, Integer> getStats(List<Integer> ids) {
		Map<Pair<Integer, Integer>, Integer> map = new HashMap<>();
		for (int i = 0; i + 1 < ids.size(); i++) {
			Pair<Integer, Integer> key = new Pair<>(ids.get(i), ids.get(i + 1));
			map.put(key, map.getOrDefault(key, 0) + 1);
		}
		return map;
	}

	private static List<Integer> merge(List<Integer> ids, Pair<Integer, Integer> pair, int idx) {
		List<Integer> newids = new ArrayList<>();
		int i = 0;
		while (i < ids.size()) {
			// if not at the very last position AND the pair matches, replace it
			if (ids.get(i).equals(pair.first()) && i < ids.size() - 1 && ids.get(i + 1).equals(pair.second())) {
				newids.add(idx);
				i += 2;
			} else {
				newids.add(ids.get(i));
				i += 1;
			}
		}
		return newids;
	}

	/**
	 * Returns list of utf-8 byte and a corresponding list of unicode strings.
	 * The reversible bpe codes work on unicode strings.
	 * This means you need a large # of unicode characters in your vocab if you want to avoid UNKs.
	 * When you're at something like a 10B token dataset you end up needing around 5K for decent coverage.
	 * This is a significant percentage of your normal, say, 32K bpe vocab.
	 * To avoid that, we want lookup tables between utf-8 bytes and unicode strings.
	 * And avoids mapping to whitespace/control characters the bpe code barfs on.
	 */
	private static Map<Integer, Integer> bytesToUnicode() {
		List<Integer> bs = new ArrayList<>();
		IntStream.rangeClosed('!', '~').forEach(bs::add);
		IntStream.rangeClosed('¡', '¬').forEach(bs::add);
		IntStream.rangeClosed('®', 'ÿ').forEach(bs::add);
		List<Integer> cs = new ArrayList<>(bs);
		int n = 0;
		for (int b = 0; b < 256; ++b) {
			if (!bs.contains(b)) {
				bs.add(b);
				cs.add(256 + n);
				n += 1;
			}
		}
		// return dict(zip(bs, cs))
				return IntStream.range(0, bs.size())
						.boxed()
						.collect(Collectors.toMap(bs::get, cs::get));
	}

	static final Map<Integer, Integer> BYTE_ENCODER = bytesToUnicode();
	static final Map<Integer, Integer> BYTE_DECODER = BYTE_ENCODER.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

	private static String replaceControlCharacters(int[] codePoints) {
		// we don't want to print control characters
		// which distort the output (e.g. \n or much worse)
		// https://stackoverflow.com/questions/4324790/removing-control-characters-from-a-string-in-python/19016117#19016117
		// http://www.unicode.org/reports/tr44/#GC_Values_Table\
		StringBuilder chars = new StringBuilder();
		for (int cp : codePoints) {
			if (Character.getType(cp) == Character.CONTROL && cp != '\n') {
				chars.append("\\u").append(HexFormat.of().toHexDigits(cp, 4)); // escape
			} else {
				chars.appendCodePoint(cp); // this character is ok
			}
		}
		return chars.toString();
	}

	private static String replaceControlCharacters(String str) {
		return replaceControlCharacters(str.codePoints().toArray());
	}
	/**
	 * Apply DeviceManager.encode on input text rendering tokenized list
	 * @param text The input String
	 * @return The List of tokenized Integers
	 */
	public List<Integer> encodeAsList(String text) {
		return DeviceManager.encode(text);
	}
	/**
	 * Apply DeviceManager.encode on input text rendering tokenized Collection
	 * @param text The input String
	 * @return The Collection of tokenized Integer class or subclass
	 */
	public Collection<? extends Integer> encodeAsCollection(String text) {
		return encodeAsList(text);
	}
	
}
