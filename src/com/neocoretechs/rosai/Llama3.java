package com.neocoretechs.rosai;

import java.io.IOException;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.neocoretechs.rosai.ffi.NativeLoader;
/**
 * Foreign Function Interface to Llama.cpp model runner to take full advantage to GPU enabled platforms.
 * Most of the internal machinery of inference is abstracted away behind the Llama.cpp native runtime,
 * despite a lot of utility function native handles being exposed for convenience purposes and for future research.
 * We trade prompt strings and tokenized responses to and fro.<p>
 * Remember: Llama models use GPT2 vocabulary while non-Llama models use Llama vocabulary!
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
public class Llama3 {
	private static final Log log = LogFactory.getLog(Llama3.class);
    public final static boolean DEBUG = false;
	// Arena
	public static Arena autoArena = Arena.ofAuto();
	public static Arena sharedArena = Arena.ofShared();
	//
    public static MethodHandle cudaGetMemInfo;
	public static MethodHandle copyFromNativeMH;
	public static MethodHandle runModelMH;
	public static MethodHandle runModelTokenizeMH;
	public static MethodHandle loadModelMH;
	public static MethodHandle stringToTokenMH;
	public static MethodHandle tokenToStringMH;
	public static MethodHandle applyChatTemplateMH;
	public static MethodHandle resetContextMH;
	public static MethodHandle getTokenBOSMH;
	public static MethodHandle getTokenEOSMH;
	public static MethodHandle getTokenEOTMH;
	
	static Options options = null;


	static {
		NativeLoader.load();
	}
	
    public static void main(String[] args) throws IOException {
        NativeLoader.loadMethods();
        options = Options.parseOptions(args);
		StringTensor s = new StringTensor(options.modelPath().toString());
		//try(Timer _ = Timer.log("load model")) {
			DeviceManager.loadModel(s, options.getMaxTokens());
		//}
			ChatFormat chatFromat = new ChatFormat();
        if (options.interactive()) {
            ChatFormat chatFormat = new ChatFormat();
            List<ChatFormat.Message> dialog = new ArrayList<ChatFormat.Message>();
            Scanner in = new Scanner(System.in);
            loop: while (true) {
            	//boolean storeDb = true;
                System.out.print("> ");
                System.out.flush();
                String userText = in.nextLine();
                switch (userText) {
                    case "/quit":
                    case "/exit": break loop;
                }
                ChatFormat.Message responseMessage = new ChatFormat.Message(chatFormat, ChatFormat.Role.USER, userText);
                dialog.add(responseMessage);
                //List<Integer> dialogTokens = chatFormat.encodeDialogPrompt(true, dialog);
                //IntTensor it = new IntTensor(dialogTokens);
                //StringTensor p = new StringTensor(new byte[dialogTokens.size()+2]);
                //DeviceManager.tokenToString(it, dialogTokens.size(), p);
                StringTensor p = chatFormat.extractDialogPrompt(dialog);
        		System.out.println("prompt:"+p);
        		int tokNum = 0;
        		IntTensor retTokens = IntTensor.allocate(options.getMaxTokens());
        		//try(Timer _ = Timer.log("run model interactive")) {
        			tokNum = DeviceManager.runModelTokenize(p, options.temperature(), options.minp(), options.topp(), retTokens, options.getMaxTokens());
        			System.out.println("Returned Tokens="+tokNum);
        		//}
        		if(tokNum == -1) {
        			log.error("Context length exceeded, exiting");
        			break;
        		}
        		String str = DeviceManager.decode(chatFormat, retTokens.toList());
        		System.out.println("returned prompt len="+str.length());
        		System.out.println(str);
                responseMessage = new ChatFormat.Message(chatFormat, ChatFormat.Role.ASSISTANT, str);
                dialog.add(responseMessage);
            }
            in.close();
        } else {
        	StringTensor p = new StringTensor(options.prompt());
    		System.out.println("prompt:"+p);
    		StringTensor it = new StringTensor(2048);
    		//try(Timer _ = Timer.log("run model")) {
    			int tokNum = DeviceManager.runModel(p, options.temperature(), options.minp(), options.topp(), it);
    			System.out.println("Tokens="+tokNum);
    		//}
        }
    }
}

record Pair<First, Second>(First first, Second second) {
}

