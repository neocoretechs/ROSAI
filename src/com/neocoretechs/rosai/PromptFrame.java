package com.neocoretechs.rosai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Encapsulates ChatFormatInterface tokenizer and manages raw and formatted token lists
 */
public final class PromptFrame {
	private static final Log log = LogFactory.getLog(PromptFrame.class);
	private ChatFormat.Message message;
	private final ChatFormat chatFormat;
	private Collection<? extends Integer> rawTokens;
	private List<Integer> formattedTokens;

	public PromptFrame(ChatFormat format) {
		this.chatFormat = format;
	}
	public void setMessage(ChatFormat.Message message) {
		this.message = message;
		this.rawTokens = DeviceManager.encode(message.content());//chatFormat.stripFormatting(message.content()));
		this.formattedTokens = DeviceManager.encode(message.toString()); // Includes headers + role
	}
	public List<Integer> getFormattedTokens() {
		return formattedTokens;
	}
	/*
	public int getBeginOfTextToken() {
		return chatFormat.getBeginOfText();
	}
	public Set<Integer> getStopTokens() {
		return chatFormat.getStopTokens();
	}
	*/
	public ChatFormat.Message getMessage() {
		return message;
	}
	public Collection<? extends Integer> getRawTokens() {
		return new ArrayList<>(rawTokens);
	}
}

