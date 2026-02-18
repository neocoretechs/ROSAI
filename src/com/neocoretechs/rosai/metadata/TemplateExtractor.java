package com.neocoretechs.rosai.metadata;

public class TemplateExtractor {
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
}
