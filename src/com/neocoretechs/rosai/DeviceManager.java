package com.neocoretechs.rosai;

import java.lang.foreign.MemorySegment;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
/**
 * DeviceManager maintains static methods that wrap the native methods loaded via the FFI that
 * call out to the Llama.cpp runtime. The memorysegments that are backing the various tensor classes
 * are extracted and the address is passed to the FFI methods as the primary method of data transfer to the native side.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
public final class DeviceManager {
	private static final Log log = LogFactory.getLog(DeviceManager.class);
	private static boolean DEBUG = true;

	public static void loadModel(StringTensor model, int contextSize) {
		MemorySegment hostSeg = model.getSegment();
		long addr = hostSeg.address();
		try {
			Llama3.loadModelMH.invokeExact(addr, contextSize);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	public static int runModel(StringTensor prompt, float temp, float min_p, float top_p, StringTensor returnTokens) {
		MemorySegment hostSeg = prompt.getSegment();
		long addr = hostSeg.address();
		MemorySegment tokSegment = returnTokens.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.runModelMH.invokeExact(addr, temp, min_p, top_p, addr2);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	public static int runModelTokenize(StringTensor prompt, float temp, float min_p, float top_p, IntTensor returnTokens, int max) {
		MemorySegment hostSeg = prompt.getSegment();
		if(DEBUG)
			log.info("prompt length:"+hostSeg.byteSize());
		long addr = hostSeg.address();
		MemorySegment tokSegment = returnTokens.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.runModelTokenizeMH.invokeExact(addr, temp, min_p, top_p, addr2, max);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	private static int stringToToken(StringTensor inStr, IntTensor retToken) {
		MemorySegment hostSeg = inStr.getSegment();
		long addr = hostSeg.address();
		MemorySegment tokSegment = retToken.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.stringToTokenMH.invokeExact(addr, addr2);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}	
	}
	
	public static int tokenToString(IntTensor inTokens, int size, StringTensor retStrings) {
		MemorySegment hostSeg = inTokens.getSegment();
		long addr = hostSeg.address();
		MemorySegment tokSegment = retStrings.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.tokenToStringMH.invokeExact(addr, size, addr2);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}	
	}
	
	public static List<Integer> encode(String string) {
		StringTensor str = new StringTensor(string);
		IntTensor it = IntTensor.allocate(Llama3.options.getMaxTokens());
		stringToToken(str, it);
		return it.toList();
	}
	/**
	 * Decode a String from a list of input tokens using tokenToString call to model.
	 * Based on stopTokens and endOfTurn fields from {@link ChatFormat}
	 * @param it The List of integer input tokens
	 * @return the resultant String.
	 */
	public static String decode(ChatFormat chatFormat, List<Integer> it) {
		//System.out.println(Arrays.toString(it.toArray()));
		int i;
		for(i = it.size()-1; i >= 0; i--) {
			if(chatFormat.getStopTokens().contains(it.get(i)))
				break;
		}
		if(i == -1)
			i = it.size()-1;
		IntTensor itt = new IntTensor(it.subList(0, i));
		if(DEBUG)
			log.info("decode input len="+i);
		StringTensor retStringTensor = new StringTensor(new byte[Llama3.options.getMaxTokens()]);
		int strLen = tokenToString(itt, i, retStringTensor);
		String retString = retStringTensor.toString();
		if(DEBUG)
			log.info("decode strLen="+retString.length());
		strLen = retString.lastIndexOf(ChatFormat.endOfTurn);
		if(strLen != -1)
			retString = retString.substring(0,strLen+ChatFormat.endOfTurn.length());
		if(DEBUG)
			log.info("decode strLen subsring="+retString.length());
		return retString;
	}
	
	public static int applyChatTemplate(MessageTensor chatSegt, StringTensor outSegt, int msgNum, int bufLen) {
		long chatSeg = chatSegt.getSegment().address();
		long outSeg = outSegt.getSegment().address();
		int outLen;
	    try {
	        outLen = (int) Llama3.applyChatTemplateMH.invokeExact(
	            chatSeg,                         // chat
	            (long) msgNum,                   // n_msg
	            true,                            // add_asst
	            outSeg,                          // buf
	            bufLen                           // len
	        );
	    } catch (Throwable t) {
	        throw new RuntimeException("apply_chat_template failed", t);
	    }
	    return outLen;
	}
	public static void resetContext() {
		try {
			Llama3.resetContextMH.invokeExact();
		} catch (Throwable e) {
			 throw new RuntimeException("Context reset failed", e);
		}
	}
}
