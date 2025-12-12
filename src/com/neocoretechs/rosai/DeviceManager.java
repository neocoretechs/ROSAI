package com.neocoretechs.rosai;

import java.lang.foreign.MemorySegment;
import java.util.List;

public final class DeviceManager {
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
	
	public static String decode(List<Integer> it) {
		IntTensor itt = new IntTensor(it);
		StringTensor retString = new StringTensor(new byte[Llama3.options.getMaxTokens()]);
		tokenToString(itt, it.size(), retString);
		return retString.toString();
	}
}
