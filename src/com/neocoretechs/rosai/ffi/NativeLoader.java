package com.neocoretechs.rosai.ffi;

import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.neocoretechs.rosai.Llama3;

public final class NativeLoader {
	public static boolean DEBUG = false;
	private static final Log log = LogFactory.getLog(NativeLoader.class);
    private static volatile boolean loaded = false;
    private NativeLoader() {}
    private enum LibraryState {
		NOT_LOADED,
		LOADING,
		LOADED
	}
	private static final AtomicReference<LibraryState> libraryLoaded = new AtomicReference<>(LibraryState.NOT_LOADED);

	static {
		NativeLoader.loadLibrary(new File(System.getProperty("java.library.path")).list());
	}
	
	public static void load() {
		NativeLoader.loadLibrary(new File(System.getProperty("java.library.path")).list());
		if(DEBUG)
			log.info("load complete..");
	}
	
	/**
	 * Tries to load the necessary library files from the given list of
	 * directories.
	 *
	 * @param paths a list of strings where each describes a directory of a library.
	 */
	public static void loadLibrary(final String[] paths) {
		if (libraryLoaded.get() == LibraryState.LOADED) {
			return;
		}
		if(libraryLoaded.compareAndSet(LibraryState.NOT_LOADED,LibraryState.LOADING)) {
			synchronized (NativeLoader.class) {
				//.out.println("Loading from paths list of length:"+paths.size());
				for (final String path : paths) {
					//if(DEBUG) log.info(path);
					if((path.endsWith(".so") || path.endsWith(".dll")) && !path.contains("rocksdb")) {
						//String fname = new File(path).getName();
						//fname = fname.substring(0,fname.indexOf("."));
						//if(DEBUG)
						//	log.info("Trying load for:"+fname);
						//System.loadLibrary(fname);
						Path p = Path.of(System.getProperty("java.library.path"),path).toAbsolutePath();
						if(DEBUG)
							log.info("Attempting load for "+p.toString());
						System.load(p.toString());
					}
				}
			}
			libraryLoaded.set(LibraryState.LOADED);
		}
		while (libraryLoaded.get() == LibraryState.LOADING) {
			try {
				log.info("Waiting for load, retry..");
				Thread.sleep(10);
			} catch(final InterruptedException e) {}
		}
	}
	
	public static void loadMethods() {
		Linker linker = Linker.nativeLinker();
		//if(DEBUG) log.info("linker:"+linker);
		SymbolLookup lookup = SymbolLookup.loaderLookup();
		//if(DEBUG) log.info("Loader:"+lookup);
		/*
		Llama3.cudaGetMemInfo = linker.downcallHandle(
				lookup.find("cudaGetMemInfo").orElseThrow(),
				FunctionDescriptor.ofVoid(
						ValueLayout.ADDRESS,    // size_t* free, writes to memorysegments
						ValueLayout.ADDRESS     // size_t* total
						));
		if(DEBUG) log.info("cudaGetMemInfo:"+Llama3.cudaGetMemInfo);
	    Llama3.copyFromNativeMH = linker.downcallHandle(
		        lookup.find("copyFromNative").orElseThrow(),
		        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG,   // uint8_t* tensor, or uint8_t** arraytensor
		                                  ValueLayout.JAVA_LONG) // size_t bytes
		    );
			if(DEBUG) log.info("copyFromNative:"+Llama3.copyFromNativeMH);
			*/
		Llama3.loadModelMH = linker.downcallHandle(
			        lookup.find("load_model").orElseThrow(),
			        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, // uint8_t* tensor model path
			        							ValueLayout.JAVA_INT)  // context size
			);
		if(DEBUG) log.info("load_model:"+Llama3.loadModelMH);
	    Llama3.runModelMH = linker.downcallHandle(
		        lookup.find("run_model").orElseThrow(),
		        FunctionDescriptor.of(ValueLayout.JAVA_INT,
		        						ValueLayout.JAVA_LONG, // prompt StringTensor
		        						ValueLayout.JAVA_FLOAT, // temp
		        						ValueLayout.JAVA_FLOAT, // mip_p
		        						ValueLayout.JAVA_FLOAT, // top_p
		        						ValueLayout.JAVA_LONG // IntTensor return tokens
		        						) // StringTensor return dialog uint8_t* tensor, or uint8_t** ArrayTensor
		    );
		if(DEBUG) log.info("run_model:"+Llama3.runModelMH);
	    Llama3.runModelTokenizeMH = linker.downcallHandle(
		        lookup.find("run_model_tokenize").orElseThrow(),
		        FunctionDescriptor.of(ValueLayout.JAVA_INT,
		        						ValueLayout.JAVA_LONG, // prompt StringTensor
		        						ValueLayout.JAVA_FLOAT, // temp
		        						ValueLayout.JAVA_FLOAT, // mip_p
		        						ValueLayout.JAVA_FLOAT, // top_p
		        						ValueLayout.JAVA_LONG // IntTensor return tokens
		        						) // StringTensor return dialog uint8_t* tensor, or uint8_t** ArrayTensor
		    );
		if(DEBUG) log.info("run_model_tokenize:"+Llama3.runModelTokenizeMH);
		Llama3.stringToTokenMH = linker.downcallHandle(
			    lookup.find("string_to_token").orElseThrow(),
			    FunctionDescriptor.of(ValueLayout.JAVA_INT,
			        					ValueLayout.JAVA_LONG, // prompt StringTensor
			        					ValueLayout.JAVA_LONG // IntTensor return tokens
			        					) // StringTensor return dialog uint8_t* tensor, or uint8_t** ArrayTensor
			    );
		if(DEBUG) log.info("string_to_token:"+Llama3.stringToTokenMH);
		Llama3.tokenToStringMH = linker.downcallHandle(
				lookup.find("token_to_string").orElseThrow(),
				FunctionDescriptor.of(ValueLayout.JAVA_INT,
				        					ValueLayout.JAVA_LONG, // IntTensor of tokens
				        					ValueLayout.JAVA_INT, // size
				        					ValueLayout.JAVA_LONG // StringTensor return string
				        					)
				);
		if(DEBUG) log.info("token_to_string:"+Llama3.tokenToStringMH);
		//EXPORT int apply_chat_template(uint8_t* chatl, size_t, bool, uint8_t*, int32_t);
		Llama3.applyChatTemplateMH = linker.downcallHandle(
		    lookup.find("apply_chat_template").orElseThrow(),
		    FunctionDescriptor.of(
		    	ValueLayout.JAVA_INT, // return size of allocated buffer
		        ValueLayout.JAVA_LONG, // chat struct pointer 
		        ValueLayout.JAVA_LONG, // number of messages
		        ValueLayout.JAVA_BOOLEAN, // add assistant
		        ValueLayout.JAVA_LONG,  // n_msg
		        ValueLayout.JAVA_INT    // len
		    )
		);
		if(DEBUG) log.info("apply_chat_template:"+Llama3.applyChatTemplateMH);
		//EXPORT void reset_context();
		Llama3.resetContextMH = linker.downcallHandle(
		        lookup.find("reset_context").orElseThrow(),
		        FunctionDescriptor.ofVoid()
		);
		if(DEBUG) log.info("reset_context:"+Llama3.resetContextMH);
		//EXPORT int get_token_bos();
		Llama3.getTokenBOSMH = linker.downcallHandle(
		        lookup.find("get_token_bos").orElseThrow(),
		        FunctionDescriptor.of(
		    	ValueLayout.JAVA_INT)
		);
		if(DEBUG) log.info("get_token_bos:"+Llama3.getTokenBOSMH);
		//EXPORT int get_token_eos();
		Llama3.getTokenEOSMH = linker.downcallHandle(
		        lookup.find("get_token_eos").orElseThrow(),
		        FunctionDescriptor.of(
		    	ValueLayout.JAVA_INT)
		);
		if(DEBUG) log.info("get_token_eos:"+Llama3.getTokenEOSMH);
		//EXPORT int get_token_eot();
		Llama3.getTokenEOTMH = linker.downcallHandle(
		        lookup.find("get_token_eot").orElseThrow(),
		        FunctionDescriptor.of(
		    	ValueLayout.JAVA_INT)
		);
		if(DEBUG) log.info("get_token_eot:"+Llama3.getTokenEOTMH);
	}
}
