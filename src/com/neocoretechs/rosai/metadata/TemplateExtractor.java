package com.neocoretechs.rosai.metadata;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.neocoretechs.rosai.ChatFormat.Role;
/**
 * Metadata fun <p>
 * <pre>
 * general.architecture contains items such as mistral, llama, qwen, magistral
 * this controls the tokenizer, if it says llama, it creates a gpt2 tokenizer
 * if it says mistral it creates a llama tokenizer!
 * if it says qwen or magistral it creates a qwen or magistral tokenizer!
 * from there you can get tokens like so int[] tokenTypes = (int[]) metadata.get("tokenizer.ggml.token_type");
 * </pre>
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2026
 */
public class TemplateExtractor {
    static final String TOKENIZER_GPT2_MODEL = "gpt2"; // Llama3 uses gpt2!
    static final String TOKENIZER_LLAMA_MODEL = "llama"; // non Llama uses llama!
    public String model = "gpt2"; // default for Llama models!
    public String name = null; // Name is based solely on name of model, they all seem to have their own ChatFormat not based on model
    
	enum modelToTemplate {
		LLM_CHAT_TEMPLATE_CHATML("chatml"),
		LLM_CHAT_TEMPLATE_LLAMA_2("llama2"),                  
		LLM_CHAT_TEMPLATE_LLAMA_2_SYS("llama2-sys"),
		LLM_CHAT_TEMPLATE_LLAMA_2_SYS_BOS("llama2-sys-bos"),    
		LLM_CHAT_TEMPLATE_LLAMA_2_SYS_STRIP("llama2-sys-strip"),         
		LLM_CHAT_TEMPLATE_MISTRAL_V1("mistral-v1"),           
		LLM_CHAT_TEMPLATE_MISTRAL_V3("mistral-v3"),
		LLM_CHAT_TEMPLATE_MISTRAL_V3_TEKKEN("mistral-v3-tekken"),        
		LLM_CHAT_TEMPLATE_MISTRAL_V7("mistral-v7"),
		LLM_CHAT_TEMPLATE_MISTRAL_V7_TEKKEN ("mistral-v7-tekken"), 
		LLM_CHAT_TEMPLATE_PHI_3("phi3"),              
		LLM_CHAT_TEMPLATE_PHI_4 ("phi4"),
		LLM_CHAT_TEMPLATE_FALCON_3("falcon3"),
		LLM_CHAT_TEMPLATE_ZEPHYR("zephyr"),
		LLM_CHAT_TEMPLATE_MONARCH("monarch"), 
		LLM_CHAT_TEMPLATE_GEMMA("gemma"),
		LLM_CHAT_TEMPLATE_ORION("orion"),
		LLM_CHAT_TEMPLATE_OPENCHAT("openchat"),
		LLM_CHAT_TEMPLATE_VICUNA("vicuna"),
		LLM_CHAT_TEMPLATE_VICUNA_ORCA("vicuna-orca"),
		LLM_CHAT_TEMPLATE_DEEPSEEK("deepseek"),
		LLM_CHAT_TEMPLATE_DEEPSEEK_2("deepseek2"),
		LLM_CHAT_TEMPLATE_DEEPSEEK_3("deepseek3"),
		LLM_CHAT_TEMPLATE_COMMAND_R("command-r"),
		LLM_CHAT_TEMPLATE_LLAMA_3("llama3"),
		LLM_CHAT_TEMPLATE_CHATGLM_3("chatglm3"),
		LLM_CHAT_TEMPLATE_CHATGLM_4("chatglm4"),
		LLM_CHAT_TEMPLATE_GLMEDGE("glmedge"),
		LLM_CHAT_TEMPLATE_MINICPM("minicpm"),
		LLM_CHAT_TEMPLATE_EXAONE_3("exaone3"),
		LLM_CHAT_TEMPLATE_EXAONE_4("exaone4"),
		LLM_CHAT_TEMPLATE_EXAONE_MOE("exaone-moe"),
		LLM_CHAT_TEMPLATE_RWKV_WORLD("rwkv-world"),
		LLM_CHAT_TEMPLATE_GRANITE("granite"),
		LLM_CHAT_TEMPLATE_GIGACHAT("gigachat"),
		LLM_CHAT_TEMPLATE_MEGREZ("megrez"),   
		LLM_CHAT_TEMPLATE_YANDEX("yandex"),
		LLM_CHAT_TEMPLATE_BAILING("bailing"),           	     
		LLM_CHAT_TEMPLATE_BAILING_THINK("bailing-think"),
		LLM_CHAT_TEMPLATE_BAILING2("bailing2"),     
		LLM_CHAT_TEMPLATE_LLAMA4("llama4"),           
		LLM_CHAT_TEMPLATE_SMOLVLM("smolvlm"),      	  
		LLM_CHAT_TEMPLATE_HUNYUAN_MOE("hunyuan-moe"),
		LLM_CHAT_TEMPLATE_OPENAI_MOE("gpt-oss"),
		LLM_CHAT_TEMPLATE_HUNYUAN_DENSE("hunyuan-dense"),
		LLM_CHAT_TEMPLATE_KIMI_K2("kimi-k2"),             
		LLM_CHAT_TEMPLATE_SEED_OSS("seed_oss"),           
		LLM_CHAT_TEMPLATE_GROK_2("grok-2"),                
		LLM_CHAT_TEMPLATE_PANGU_EMBED("pangu-embedded"),    
		LLM_CHAT_TEMPLATE_SOLAR_OPEN("solar-open");
		private final String model;
		private modelToTemplate(String model) {
			this.model = model;
		}
		@Override
		public String toString() {
			return this.model;
		}
	};
	/*
	// This is the Llama, Mistral etc Role enum
	private enum RoleEnum implements Role {
		SYSTEM("system"),
		USER("user"),
		ASSISTANT("assistant"),
		ENDOFTURN("<|eot_id|>"); // llama3 specific, overwritten in ctor
		private final String role;
		RoleEnum(String role) {
			this.role = role;
		}
		@Override
		public String getRole() {
			return role;
		}
		@Override
		public String collect() {
			return Stream.of(values())
            .map(Role::getRole)
            .collect(Collectors.joining("|"));
		}
		@Override
		public String toString() {
			return role;
		}
	}
	*/
	public record RoleDelims(
			String user,
			String assistant,
			String system,
			String endOfTurn
			) {}

	public void metaExtract(Map<String, Object> meta) {
		//general.architecture=llama
		// general.architecture contains items such as mistral, llama, qwen, magistral
		// this controls the tokenizer, if it says llama, it creates a gpt2 tokenizer
		// if it says mistral it creates a llama tokenizer!
		// if it says qwen or magistral it creates a qwen or magistral tokenizer!
		// from there you can get tokens like so int[] tokenTypes = (int[]) metadata.get("tokenizer.ggml.token_type");
		model = (String) meta.getOrDefault("general.architecture","llama3");
		// Wherein Llama models metadata.get("tokenizer.ggml.model") = gpt2
		// and Mistral uses metadata.get("tokenizer.ggml.model") = llama.
		// general.name=Llama 3.3 70B Instruct
		name = ((String) meta.get("general.name")).toLowerCase();
		if(name.contains("llama") && name.contains("3")) // Meta Llama etc. etc.
			name = "llama3";
		else
			if(name.contains("llama") && name.contains("4")) // Meta Llama etc. etc.
				name = "llama4";
			else
				if(name.contains("llama") && name.contains("2")) {// Meta Llama etc. etc.
					if(name.contains("sys")) {
						if(name.contains("bos"))
							name = "llama2-sys-bos";
						else
							if(name.contains("strip"))
								name = "llama2-sys-strip";
							else
								name = "llama2-sys";
					} else
						name = "llama2";
				} else
					if(name.contains("mistral")  && name.contains("1")) //models--mistralai etc. etc.
						name="mistral-v1";
					else
						if(name.contains("mistral")  && name.contains("3")) { //models--mistralai etc. etc.
							if(name.contains("tekken"))
								name="mistral-v3-tekken";
							else
								name="mistral-v3";
						} else
							if(name.contains("mistral")  && name.contains("7")) {
								if(name.contains("tekken"))//models--mistralai etc. etc.
									name = "mistral-v7-tekken";
								else
									name="mistral-v7";
							}	
							else
								if(name.contains("qwen"))
									name="qwen";
								else
									if(name.contains("magistral"))
										name="mistral";
									else
										if(name.contains("phi") && name.contains("3")) // Meta Llama etc. etc.
											name = "phi3";
										else
											if(name.contains("phi") && name.contains("4")) // Meta Llama etc. etc.
												name = "phi4";
											else
												if(name.contains("gemma"))
													name = "gemma";
												else
													if(name.contains("vicuna")) {
														if(name.contains("orca"))
														name = "vicuna-orca";
														else
															name = "vicuna";
													} else
														if(name.contains("deepseek")) {
															if(name.contains("2"))
																name = "deepseek2";
															else
																if(name.contains("3"))
																	name = "deepseek3";
																else
																	name = "deepseek";
														} else
															if(name.contains("grok"))
																name = "grok-2";


	}
	/**
	 * Once you have the metadata: "tokenizer.chat_format": "qwen2"
	 */
	public static final Map<String, RoleDelims> CHAT_TEMPLATES = Map.ofEntries(
			// --- ChatML (OpenAI-style) ---
			Map.entry("chatml", new RoleDelims(
					"<|im_start|>user\n",
					"<|im_start|>assistant\n",
					null,
					"<|im_end|>"
					)),

			// --- Llama 2 variants ---
			Map.entry("llama2", new RoleDelims("User:", "Assistant:", null, "")),
			Map.entry("llama2-sys", new RoleDelims("<<SYS>>\n", "Assistant:", "<<SYS>>\n", "")),
			Map.entry("llama2-sys-bos", new RoleDelims("<<SYS>>\n", "Assistant:", "<<SYS>>\n", "")),
			Map.entry("llama2-sys-strip", new RoleDelims("<<SYS>>\n", "Assistant:", "<<SYS>>\n", "")),

			// --- Llama 3.x ---
			Map.entry("llama3", new RoleDelims(
					"<|user|>",
					"<|assistant|>",
					"<|system|>",
					"<|eot_id|>"
					)),

			// --- Llama 4 (future-proof) ---
			Map.entry("llama4", new RoleDelims(
					"<|user|>",
					"<|assistant|>",
					"<|system|>",
					"<|eot_id|>"
					)),

			// --- Mistral variants ---
			Map.entry("mistral-v1", new RoleDelims("[INST] ", " [/INST]", null, "")),
			Map.entry("mistral-v3", new RoleDelims("[INST] ", " [/INST]", null, "")),
			Map.entry("mistral-v3-tekken", new RoleDelims("[INST] ", " [/INST]", null, "")),
			Map.entry("mistral-v7", new RoleDelims("[INST] ", " [/INST]", null, "")),
			Map.entry("mistral-v7-tekken", new RoleDelims("[INST] ", " [/INST]", null, "")),

			// --- Phi family ---
			Map.entry("phi3", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end|>")),
			Map.entry("phi4", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end|>")),

			// --- Falcon 3 ---
			Map.entry("falcon3", new RoleDelims("User:", "Assistant:", null, "")),

			// --- Zephyr ---
			Map.entry("zephyr", new RoleDelims("<|user|>", "<|assistant|>", null, "")),

			// --- Monarch ---
			Map.entry("monarch", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Gemma 2 ---
			Map.entry("gemma", new RoleDelims(
					"<start_of_turn>user\n",
					"<start_of_turn>model\n",
					"<start_of_turn>system\n",
					"<end_of_turn>"
					)),

			// --- Orion ---
			Map.entry("orion", new RoleDelims("User:", "Assistant:", null, "")),

			// --- OpenChat ---
			Map.entry("openchat", new RoleDelims("User:", "Assistant:", null, "")),

			// --- Vicuna ---
			Map.entry("vicuna", new RoleDelims("USER:", "ASSISTANT:", null, "")),
			Map.entry("vicuna-orca", new RoleDelims("USER:", "ASSISTANT:", null, "")),

			// --- DeepSeek ---
			Map.entry("deepseek", new RoleDelims("User:", "Assistant:", null, "<｜End｜>")),
			Map.entry("deepseek2", new RoleDelims("User:", "Assistant:", null, "<｜End｜>")),
			Map.entry("deepseek3", new RoleDelims("User:", "Assistant:", null, "<｜End｜>")),

			// --- Command-R ---
			Map.entry("command-r", new RoleDelims("<|user|>", "<|assistant|>", null, "<|eot_id|>")),

			// --- ChatGLM ---
			Map.entry("chatglm3", new RoleDelims("User:", "Assistant:", null, "")),
			Map.entry("chatglm4", new RoleDelims("User:", "Assistant:", null, "")),
			Map.entry("glmedge", new RoleDelims("User:", "Assistant:", null, "")),

			// --- MiniCPM ---
			Map.entry("minicpm", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Exaone ---
			Map.entry("exaone3", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),
			Map.entry("exaone4", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),
			Map.entry("exaone-moe", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- RWKV ---
			Map.entry("rwkv-world", new RoleDelims("User:", "Assistant:", null, "")),

			// --- Granite ---
			Map.entry("granite", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- GigaChat ---
			Map.entry("gigachat", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Megrez ---
			Map.entry("megrez", new RoleDelims("<|user|>", "<|assistant|>", null, "")),

			// --- Yandex ---
			Map.entry("yandex", new RoleDelims("User:", "Assistant:", null, "")),

			// --- Bailing ---
			Map.entry("bailing", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),
			Map.entry("bailing-think", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),
			Map.entry("bailing2", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- SmolVLM ---
			Map.entry("smolvlm", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Hunyuan ---
			Map.entry("hunyuan-moe", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),
			Map.entry("hunyuan-dense", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- OpenAI OSS (gpt-oss) ---
			Map.entry("gpt-oss", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Kimi ---
			Map.entry("kimi-k2", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Seed OSS ---
			Map.entry("seed_oss", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Grok 2 ---
			Map.entry("grok-2", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Pangu ---
			Map.entry("pangu-embedded", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>")),

			// --- Solar ---
			Map.entry("solar-open", new RoleDelims("<|user|>", "<|assistant|>", null, "<|end_of_turn|>"))
			);

}