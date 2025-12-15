package com.neocoretechs.rosai;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
/**
 * Tensor of ChatFormat.message, backed by MemorySegment, implementing Comparable and Externalizable
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
public class MessageTensor implements Externalizable, Comparable {
	public static boolean DEBUG = false;
	MemorySegment memorySegment;

	static final GroupLayout LLAMA_CHAT_MESSAGE = MemoryLayout.structLayout(
	        ValueLayout.ADDRESS.withName("role"),
	        ValueLayout.ADDRESS.withName("content")
	);
	
	public MessageTensor() {}
	
	public MemorySegment getSegment() {
		return memorySegment;
	}	
	
	public void allocate(int utf8Bytes) {
		memorySegment = getArena().allocate(utf8Bytes);
	}
	
	public void copy(byte[] utf8Bytes) {
		allocate(utf8Bytes.length);
		memorySegment.copyFrom(MemorySegment.ofArray(utf8Bytes));
	}
	
	public int size() {
		return (int) memorySegment.byteSize();
	}
	
	public static StringTensor allocOutput(int totalMsgSize) {
		return new StringTensor(new byte[totalMsgSize*2+1024]);
	}
	
	public int allocate(List<ChatFormat.Message> msgs) {
		memorySegment = getArena().allocate(LLAMA_CHAT_MESSAGE, msgs.size());
		int totalMsgSize = 0;
		StringTensor[] stRoles = new StringTensor[msgs.size()];
		StringTensor[] stContent = new StringTensor[msgs.size()];
		for (int i = 0; i < msgs.size(); i++) {
			ChatFormat.Message m = msgs.get(i);
			totalMsgSize += m.content().length();
			MemorySegment struct = memorySegment.asSlice(
					i * LLAMA_CHAT_MESSAGE.byteSize(),
					LLAMA_CHAT_MESSAGE.byteSize()
					);
			// Allocate C strings
			stRoles[i] = new StringTensor(m.role().getRole().toLowerCase());
			stContent[i] = new StringTensor(m.content());
			// Write pointers into struct
			struct.set(ValueLayout.ADDRESS, LLAMA_CHAT_MESSAGE.byteOffset(PathElement.groupElement("role")), stRoles[i].getSegment());
			struct.set(ValueLayout.ADDRESS, LLAMA_CHAT_MESSAGE.byteOffset(PathElement.groupElement("content")), stContent[i].getSegment());
		}
		return totalMsgSize;
	}
	
	public String applyChatTemplate(List<ChatFormat.Message> msgs, boolean addAssistantPrompt) {
	    // 1. Build llama_chat_message[] in native memory
	    MessageTensor chatTensor = new MessageTensor();
	    int totalMsgSize = chatTensor.allocate(msgs); // alloc + fills struct array
	    // 2. Allocate output buffer
	    StringTensor out = MessageTensor.allocOutput(totalMsgSize);
	    int bufLen = out.size();                                 // len
	    DeviceManager.applyChatTemplate(chatTensor, out, msgs.size(), bufLen, addAssistantPrompt);
	    // 3. Convert UTF8 C string in out.buffer to Java String
	    return out.toString();
	}
	
	public Arena getArena() {
		return Llama3.sharedArena;
	}

	@Override
	public int compareTo(Object o) {
		return Arrays.compare(memorySegment.toArray(ValueLayout.JAVA_BYTE),((MessageTensor)o).getSegment().toArray(ValueLayout.JAVA_BYTE));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(size());
		out.write(memorySegment.asByteBuffer().array());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int vsize = in.readInt();
		// allocate off-heap space for headSize floats
		memorySegment = getArena().allocate(ValueLayout.JAVA_BYTE, vsize);
		byte[] tmp = new byte[vsize];
		in.readFully(tmp);
		memorySegment.copyFrom(MemorySegment.ofArray(tmp));
	}

}
