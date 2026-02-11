package com.neocoretechs.rosai.metadata;

import java.io.IOException;

import java.nio.channels.FileChannel;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.Map;


public class MetadataReader {
	private static boolean DISPLAY_METADATA;

	public static GGUF loadModel(Path modelPath) throws IOException {
		try (FileChannel fileChannel = FileChannel.open(modelPath)) {     
			GGUF gguf = new GGUF();
			gguf.loadModelImpl(fileChannel);
			return gguf;
		}
	}

	public static void showMetadata(GGUF gguf) throws IOException {
		Map<String, Object> metadata = gguf.getMetadata();
		System.out.println("Begin GGUF Metadata:");
		metadata.forEach((k, v) -> {
			String valueStr;
			if (v != null && v.getClass().isArray()) {
				Class<?> componentType = v.getClass().getComponentType();
				if (componentType == int.class) {
					valueStr = Arrays.toString((int[]) v);
				} else if (componentType == byte.class) {
					valueStr = Arrays.toString((byte[]) v);
				} else if (componentType == double.class) {
					valueStr = Arrays.toString((double[]) v);
				} else if (componentType == boolean.class) {
					valueStr = Arrays.toString((boolean[]) v);
				} else if (componentType == char.class) {
					valueStr = Arrays.toString((char[]) v);
				} else if (componentType == long.class) {
					valueStr = Arrays.toString((long[]) v);
				} else if (componentType == float.class) {
					valueStr = Arrays.toString((float[]) v);
				} else if (componentType == short.class) {
					valueStr = Arrays.toString((short[]) v);
				} else {
					valueStr = Arrays.toString((Object[]) v); // for Object arrays
				}
			} else {
				valueStr = String.valueOf(v);
			}
			System.out.println(k + "=" + valueStr);
		});
		System.out.println("End GGUF Metadata.\r\n");
	}
	
	public static void main(String[] args) throws Exception {
		showMetadata(loadModel(Path.of(args[0])));
	}

}