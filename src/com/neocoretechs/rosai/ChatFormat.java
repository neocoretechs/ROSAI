package com.neocoretechs.rosai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utility tailored for Llama 3 instruct prompt format.
 */
public class ChatFormat {
	private static final Log log = LogFactory.getLog(ChatFormat.class);
	public static boolean DEBUG = true;
	public static int endOfTurn;
	public static int endOfSentence;
	public static int endOfText;
	public static Set<Integer> stopTokens;
	public ChatFormat() {
		try {
			//endOfTurn = (int) Llama3.getTokenEOTMH.invokeExact();
			endOfSentence = (int) Llama3.getTokenEOSMH.invokeExact();
			//System.out.println("eot="+endOfTurn+" eos="+endOfSentence);
			StringTensor buf = new StringTensor("<|eot_id|>");
			IntTensor it = IntTensor.allocate(8);
			DeviceManager.stringToToken(buf, it);
			endOfText = it.getInt(0);
			endOfTurn = it.getInt(1);
			//System.out.println("eot="+endOfTurn+" eoText="+endOfText+" int[0]="+it.getInt(0));
			stopTokens = Set.of(endOfText, endOfTurn);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Encode list of supplied messages into tokenized List, applying chat templates
	 * @param appendAssistantTurn true to add a blank ASSISTANT header at the end of the list of prompts
	 * @param dialog List of messages to tokenize
	 * @return the tokenized list of templatized messages
	 */
	public List<Integer> encodeDialogPrompt(boolean appendAssistantTurn, List<ChatFormat.Message> dialog) {
		MessageTensor mt = new MessageTensor(dialog);
		StringTensor st = mt.applyChatTemplate(appendAssistantTurn);
		String resStr = st.toString();
		if(DEBUG)
			log.info(this.getClass().getName()+".encodeDialogPrompt="+resStr);
		return DeviceManager.encode(resStr);
	}
	/**
	 * Encode list of supplied messages into dialog text in StringTensor, applying chat templates
	 * @param appendAssistantTurn appendAssistantTurn true to add a blank ASSISTANT header at the end of the list of prompts
	 * @param dialog list of messages to process
	 * @return the StringTensor of templatized messages
	 */
	public StringTensor extractDialogPrompt(boolean appendAssistantTurn, List<Message> dialog) {
		MessageTensor mt = new MessageTensor(dialog);
		StringTensor st = mt.applyChatTemplate(appendAssistantTurn);
		if(DEBUG)
			log.info(this.getClass().getName()+".extractDialogPrompt="+st.toString());
		return st;
	}

	private String stripFormatting(String input) {
		return input.replaceAll("<\\|.*?\\|>", "")
				.replaceAll("\\*+", "")
				.replaceAll("(?m)^USER:|AI:", "")
				.trim();
	}

	public record Message(ChatFormat.Role role, String content) {
		@Override
		public String toString() {
			ArrayList<Message> tr = new ArrayList<Message>(1);
			tr.add(this);
			return MessageTensor.applyChatTemplate(tr, false);
		}
	}

	public enum Role {
		SYSTEM("SYSTEM"),
		USER("USER"),
		ASSISTANT("ASSISTANT");
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

	private List<Integer> encodeAsList(String text) {
		StringTensor st = new StringTensor(text);
		IntTensor it = IntTensor.allocate(2048);
		int toks = DeviceManager.stringToToken(st, it);
		IntTensor trimToks = new IntTensor(it, toks);
		return Arrays.stream(trimToks.toArray()).boxed().toList();
	}

	private Collection<? extends Integer> encodeAsCollection(String text) {
		return encodeAsList(text);
	}
	
}
