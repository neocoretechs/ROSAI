package com.neocoretechs.rosai;

import java.lang.foreign.MemorySegment;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class DeviceManager {
	private static final Log log = LogFactory.getLog(DeviceManager.class);
	private static boolean DEBUG = false;

	public static void loadModel(StringTensor model, int contextSize) {
		MemorySegment hostSeg = model.getSegment();
		long addr = hostSeg.address();
		try {
			Llama3.loadModelMH.invokeExact(addr, contextSize);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	public static int runModel(StringTensor prompt, float temp, float min_p, float top_p, IntTensor returnTokens) {
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
	public static int runModelTokenize(StringTensor prompt, float temp, float min_p, float top_p, IntTensor returnTokens) {
		MemorySegment hostSeg = prompt.getSegment();
		long addr = hostSeg.address();
		MemorySegment tokSegment = returnTokens.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.runModelTokenizeMH.invokeExact(addr, temp, min_p, top_p, addr2);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	public static int stringToToken(StringTensor inStr, IntTensor retToken) {
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
		List<Integer> findEot = inTokens.toList();
		int endSize = size;
		MemorySegment hostSeg = inTokens.getSegment();
		for(int i = 0; i < findEot.size(); i++) {
			if(findEot.get(i) == ChatFormat.endOfTurn) {
				IntTensor newInt;
				if(findEot.size() == i+1)
					newInt = new IntTensor(findEot.subList(0, i+1));
				else
					newInt = new IntTensor(findEot.subList(0, i+2));
				hostSeg = newInt.getSegment();
				endSize = newInt.size();
			}
		}
		
		long addr = hostSeg.address();
		MemorySegment tokSegment = retStrings.getSegment();
		long addr2 = tokSegment.address();
		try {
			return (int) Llama3.tokenToStringMH.invokeExact(addr, endSize, addr2);
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
	
	public static String decode(List<Integer> it) {
		IntTensor itt = new IntTensor(it);
		StringTensor retString = new StringTensor(new byte[Llama3.options.getMaxTokens()]);
		int strLen = tokenToString(itt, it.size(), retString);
		if(strLen > Llama3.options.getMaxTokens()) {
			log.info("Decoded string exceeds context length:"+strLen+" = ["+retString.toString().substring(0,Llama3.options.getMaxTokens()-1)+"]");
			strLen = Llama3.options.getMaxTokens();
		}
		return retString.toString().substring(0,strLen-1);
	}
}
