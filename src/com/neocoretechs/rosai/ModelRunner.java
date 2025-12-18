/**
 * COMPILE_OPTIONS --add-modules=jdk.incubator.vector <br>
 * RUNTIME_OPTIONS --add-modules=jdk.incubator.vector -Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0 <br>
 * <p>
 * Practical inference in Java, with help. <o>
 * Supports llama.cpp's GGUF format. <p>
 * Multi-threaded matrix vector multiplication routines implemented using Java's Vector API. <p>
 * Accepts commands from RosJavaLite bus topics, including sensors and status, and fuses those
 * into coherent responses to perform embodied field robotics. Derived from Oracle model runner.
 * Uses LSH indexing and semantic retrieval to provide virtually unlimited context with semantic augmentation.<p>
 * Remember: Llama models use GPT2 vocabulary while non-Llama models use Llama vocabulary!
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
package com.neocoretechs.rosai;

import stereo_msgs.StereoImage;
import trajectory_msgs.ComeToHeadingStamped;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.ros.concurrent.CancellableLoop;
import org.ros.concurrent.CircularBlockingDeque;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import org.json.JSONObject;

import com.neocoretechs.relatrix.client.asynch.AsynchRelatrixClientTransaction;
import com.neocoretechs.relatrix.parallel.SynchronizedThreadManager;
import com.neocoretechs.rocksack.TransactionId;
import com.neocoretechs.rosai.ffi.NativeLoader;
import com.neocoretechs.rosai.relatrix.RelatrixLSH;

import diagnostic_msgs.DiagnosticStatus;
import diagnostic_msgs.KeyValue;

import com.neocoretechs.rocksack.Alias;
/**
 * Execute the ROSJavaLite node processing in coming messages from the various subscriptions, format them, and route them
 * to the model runner for processing. Maintain the context via the LSH indexing and semantic retrieval. Call out to
 * Llama.cpp via the FFI and {@link DeviceManager} methods.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
public class ModelRunner extends AbstractNodeMain {
	private static final Log log = LogFactory.getLog(ModelRunner.class);
	// Batch-size used in prompt evaluation.
	public final static boolean DEBUG = true;
	public static boolean DISPLAY_METADATA = false;
	AsynchRelatrixClientTransaction dbClient = null;
	//static RelatrixTransaction dbClient = null;
	TransactionId xid = null;
	Alias tensorAlias = null;
	// metadata dump
	public static BufferedWriter outputStream = null;
	public static PrintWriter output = null;
	public static FileWriter fileWriter = null;
	private static boolean onceThrough = false;

	public static final String SYSTEM_PROMPT = "/system_prompt";
	public static final String USER_PROMPT = "/user_prompt";
	public static final String ASSIST_PROMPT = "/assist_prompt";
	public static final String LLM = "/model";

	CircularBlockingDeque<String> messageQueue = new CircularBlockingDeque<String>(1024);

	protected Object mutex = new Object();
	protected CountDownLatch dbLatch = new CountDownLatch(1);

	static long MESSAGE_THRESHOLD = 5000; // ms minimum between subscribed message reception
	static long lastImageTime = System.currentTimeMillis();

	static RelatrixLSH relatrixLSH = null;

	static class EulerTime {
		sensor_msgs.Imu euler;
		long eulerTime = 0L;
	}
	EulerTime euler = new EulerTime();

	static class RangeTime {
		std_msgs.String range;
		long rangeTime = 0L;
		public String toJSON() {
			return String.format("{range=%s}", range.getData());
		}
	}
	RangeTime ranges = new RangeTime();

	/**
	 * Parse the command line for url and xpath directive, if link encountered, recursively parse.
	 * Impersonate user agents Mozilla, Crapple, Chrome
	 * @param urlc  link 
	 * @param xPath xpath directive
	 * @return The Element that matches directive
	 */
	private static Element parseLinks(String urlc, String xPath) {
		//try {	
		Document doc = null;
		try {
			doc = Jsoup.connect(urlc)
					.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
					.get();
		} catch(IOException ioe) {
			ioe.printStackTrace();
			return null;
		}
		Element result = null;
		Elements results = null;
		//for(int i = 1; i < urlc.length; i++) {
		//	results = doc.select(urlc[i]);
		//}
		results = doc.selectXpath(xPath);
		if(results == null)
			return null;
		result = results.first();
		if(result == null)
			return null;
		if(result.is("a"))
			return parseLinks(result.attr("href"),"//a");
		return result;
		//System.out.printf("toString:%s text:%s wholeText:%s%n", result.toString(),result.text(),result.wholeText());
		//System.out.printf("result is a:%b result is a[href]:%b%n",result.is("a"),result.is("a[href]"));
		//} catch(MalformedURLException e) {
		//	e.printStackTrace();
		//}
		//return null;
	}

	/**
	 * command /recalltime 
	 * arg day time to end day time
	 * @param query the command line with command times, start, end
	 * @return String of Result instances from db that contain 2 elements of question/answer string in time range
	 */
	private static String parseTime(String... query) {
		CompletableFuture<Stream> s;
		String tq,tqe;
		LocalDateTime localDateTime;
		long millis,millise;
		if(query == null)
			return null;
		if(query.length == 5) {
			// day time to end day time
			tq = String.format("%s %s", query[1], query[2]);
			tqe = String.format("%s %s", query[3], query[4]);
			localDateTime = LocalDateTime.parse(tq, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
			millis = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			localDateTime = LocalDateTime.parse(tqe, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
			millise = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			//s = dbClient.findSubStream(xid,'*','?','?',millis,millise,String.class,String.class);
			StringBuilder sb = new StringBuilder();
			/*
    		try {
    			s.get().forEach(e->{
    				sb.append(((Result)e).get(0));
    				sb.append(((Result)e).get(1));
    			});
    		} catch(InterruptedException | ExecutionException ie) {}
			 */
			return sb.toString();
		}
		return null;
	}

	@Override
	public GraphName getDefaultNodeName() {
		return GraphName.of("llm");
	}

	@Override
	public void onStart(final ConnectedNode connectedNode) {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
		    System.err.println("Uncaught in thread " + t + ": " + e);
		    e.printStackTrace();
		});
		SynchronizedThreadManager.getInstance().init(new String[] {"LLM","DB"});
		NativeLoader.loadMethods();
		//
		// Extract the command line options and parse them into the model options class.
		// getNodeArguments returns args with := remappings removed
		//
		List<String> nodeArgs = connectedNode.getNodeConfiguration().getCommandLineLoader().getNodeArguments();
		// first arg is ROS node, process the rest
		ArrayList<String> opts = new ArrayList<>();
		for(int i = 1; i< nodeArgs.size(); i++)
			opts.add(nodeArgs.get(i));
		//System.out.println("Args:"+Arrays.toString(nodeArgs.toArray(new String[nodeArgs.size()])));
		//
		// NOTE: use options.getMaxTokens() from here on out after we parse metadata, as the maxtokens() value may be -1 indicating metadata
		// contextLength is used for maximum context size. 
		//
		Llama3.options = Options.parseOptions(opts);
		StringTensor s = new StringTensor(Llama3.options.modelPath().toString());
		//try(Timer _ = Timer.log("load model")) {
			DeviceManager.loadModel(s, Llama3.options.getMaxTokens());
		//}
		try {
			dbClient = connectedNode.getRelatrixClient();
			//dbClient.setTablespace("D:/etc/Relatrix/db/test/ai");
			//try {
			xid = dbClient.getTransactionId();
			//} catch (IllegalAccessException | ClassNotFoundException e) {}
			//tensorAlias = new Alias("Tensors");
			//try {
			//	if(dbClient.getAlias(tensorAlias).get() == null)
			//		dbClient.setRelativeAlias(tensorAlias);
			//} catch(ExecutionException | InterruptedException ie) {}
			if(DEBUG)
				log.info("Relatrix transaction Id:"+xid);
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		//
		// Start new thread for balance of model
		//
		SynchronizedThreadManager.getInstance().spin(new Runnable() {
			@Override
			public void run() {
				relatrixLSH = new RelatrixLSH(dbClient, Llama3.options.getMaxTokens());
				// Chat format seems solely based on individual model, so we extract a name in model loader from Metada general.name
				// set up the preamble system directives
				List<Integer> promptTokens = new ArrayList<>();
				//promptTokens.add(chatFormat.getBeginOfText());
				List<ChatFormat.Message> prompts = SystemPrompts.getSystemMessages();
				promptTokens.addAll(ChatFormat.encodeDialogPrompt(false, prompts));
				Optional<String> response = processMessage(promptTokens);
				if(response.isPresent() && response.get().length() > 0) {
					if(DEBUG)
						log.info("***Queueing from system preamble:"+response.get());
					ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, response.get());
					relatrixLSH.addInteraction(System.currentTimeMillis(), ChatFormat.Role.SYSTEM, promptTokens, responseMessage.encode());
					try {
						messageQueue.addLastWait(response.get());
					} catch(InterruptedException ie) {}
				}
				// See if we preload DB with interactions
				if(Llama3.options.preload()) {
					try {
						String fileName = Llama3.options.modelPath().getFileName().toString();
						int dotIndex = fileName.lastIndexOf('.');     
						fileName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
						SystemPrompts.frontloadDb(relatrixLSH, fileName+".txt");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				dbLatch.countDown();
			}
		},"DB");
		//
		// Set up publisher
		//final Log log = connectedNode.getLog();
		final Publisher<std_msgs.String> pubmodel = connectedNode.newPublisher(LLM, std_msgs.String._TYPE);
		// Subscribers
		final Subscriber<std_msgs.String> subsystem = connectedNode.newSubscriber(SYSTEM_PROMPT, std_msgs.String._TYPE);
		final Subscriber<std_msgs.String> subsuser = connectedNode.newSubscriber(USER_PROMPT, std_msgs.String._TYPE);
		final Subscriber<stereo_msgs.StereoImage> subsobjd = connectedNode.newSubscriber("/stereo_msgs/ObjectDetect", stereo_msgs.StereoImage._TYPE);
		final Subscriber<sensor_msgs.Imu> subsimu = connectedNode.newSubscriber("/sensor_msgs/Imu", sensor_msgs.Imu._TYPE);
		final Subscriber<std_msgs.String> subsrange = connectedNode.newSubscriber("/sensor_msgs/range",std_msgs.String._TYPE);
		final Subscriber<diagnostic_msgs.DiagnosticStatus> subsbat = connectedNode.newSubscriber("robocore/status", diagnostic_msgs.DiagnosticStatus._TYPE);
		//
		// set up subscriber callback for object detection messages
		//
		subsobjd.addMessageListener(new MessageListener<stereo_msgs.StereoImage>() {
			@Override
			public void onNewMessage(StereoImage message) {
				try {
					dbLatch.await();
				} catch (InterruptedException e) { return; }
				ByteBuffer buf = message.getData();
				String sbuf = new String(buf.array(), buf.position(), buf.remaining(), StandardCharsets.UTF_8);
				long imageTime = System.currentTimeMillis();
				if((imageTime-lastImageTime) >= MESSAGE_THRESHOLD) {
					lastImageTime = imageTime;
					processRole(sbuf, ChatFormat.Role.USER);
				}
			}
		});

		subsuser.addMessageListener(new MessageListener<std_msgs.String>() {
			@Override
			public void onNewMessage(std_msgs.String message) {
				try {
					dbLatch.await();
				} catch (InterruptedException e) { return; }
				processRole(message.getData(), ChatFormat.Role.USER);
			}
		});

		subsystem.addMessageListener(new MessageListener<std_msgs.String>() {
			@Override
			public void onNewMessage(std_msgs.String message) {
				try {
					dbLatch.await();
				} catch (InterruptedException e) { return; }
				processRole(message.getData(), ChatFormat.Role.SYSTEM);
			}
		});
		//
		// update all TimedImage in the queue that match the current timestamp to 1 ms with the current
		// IMU reading
		//
		subsimu.addMessageListener(new MessageListener<sensor_msgs.Imu>() {
			@Override
			public void onNewMessage(sensor_msgs.Imu message) {
				try {
					dbLatch.await();
				} catch (InterruptedException e) { return; }
				synchronized(euler) {
					if(euler.euler == null) {
						euler.euler = message;
						euler.eulerTime = System.currentTimeMillis();
						processRole("IMU update:\n"+euler.euler.toJSON(), ChatFormat.Role.USER);
					} else
						if(euler.euler.getCompassHeadingDegrees() != message.getCompassHeadingDegrees() ||
						euler.euler.getRoll() != message.getRoll() ||
						euler.euler.getPitch() != message.getPitch()) {
							euler.euler = message;
							if((System.currentTimeMillis() - euler.eulerTime) >= MESSAGE_THRESHOLD) {
								euler.eulerTime = System.currentTimeMillis();
								processRole("IMU update:\n"+euler.euler.toJSON(), ChatFormat.Role.USER);
							}
						}
				}
			}
		});
		//
		// Ultrasonic distance sensor also timestamped and correlated
		//
		subsrange.addMessageListener(new MessageListener<std_msgs.String>() {
			@Override
			public void onNewMessage(std_msgs.String message) {
				try {
					dbLatch.await();
				} catch (InterruptedException e) { return; }
				synchronized(ranges) {
					if(ranges.range == null) {
						ranges.range = message; //Float.parseFloat(message.getData());
						ranges.rangeTime = System.currentTimeMillis();
						processRole("Nearest distance update:\n"+ranges.toJSON(), ChatFormat.Role.USER);
					} else
						if(ranges.range.getData() != message.getData()) {
							ranges.range = message; //Float.parseFloat(message.getData());
							if((System.currentTimeMillis() - ranges.rangeTime) >= MESSAGE_THRESHOLD) {
								ranges.rangeTime = System.currentTimeMillis();
								processRole("Nearest distance update:\n"+ranges.toJSON(), ChatFormat.Role.USER);
							}
						}
				}
			}
		});

		subsbat.addMessageListener(new MessageListener<diagnostic_msgs.DiagnosticStatus>() {
			@Override
			public void onNewMessage(DiagnosticStatus message) {
				//System.out.println(message.getHardwareId()+" Status "+message.getMessage());
				StringBuilder sb = new StringBuilder();
				sb.append("MessageName:");
				sb.append(message.getName());
				sb.append(" Level:");
				sb.append(message.getLevel()+"\r\n");
				sb.append(message.getMessage()+"\r\n");
				sb.append(message.getHardwareId()+"\r\n");
				List<KeyValue> diagMsgs = message.getValues();
				if( diagMsgs != null ) {
					for( KeyValue msg : diagMsgs) {
						sb.append(msg.getKey()+" ");
						if( msg.getValue() != null ) {
							sb.append(msg.getValue()+"\r\n");
						}
					}
					if(DEBUG)
						System.out.println(sb.toString());
					processRole(sb.toString(), ChatFormat.Role.USER);
				}
			}
		});

		/**
		 * Main publishing loop. Essentially we are publishing the data in whatever state its in.
		 * This CancellableLoop will be canceled automatically when the node shuts down
		 */
		connectedNode.executeCancellableLoop(new CancellableLoop() {
			private int sequenceNumber;
			@Override
			protected void setup() {
				sequenceNumber = 0;
			}
			@Override
			protected void loop() throws InterruptedException {
				//log.info(connectedNode.getName()+" "+sequenceNumber);		
				//std_msgs.Header imghead = connectedNode.getTopicMessageFactory().newFromType(std_msgs.Header._TYPE);
				//imghead.setSeq(sequenceNumber);
				//org.ros.message.Time tst = connectedNode.getCurrentTime();
				//imghead.setStamp(tst);
				//imghead.setFrameId(tst.toString());
				// block until we have a message, take from head of queue
				String responseData = messageQueue.takeFirstNotify();
				std_msgs.String pubmess = pubmodel.newMessage();
				pubmess.setData(responseData);
				//pubmess.setHeader(imghead);
				//log.info("Publishing "+responseData);
				pubmodel.publish(pubmess);
				sequenceNumber++;
			}
		});
	} // onStart
	
	/**
	 * Present the tokenized prompt and perform forward inference using native Llama.cpp callout. Get back the response token list and process it.
	 * @param promptTokens List of prompt tokens
	 * @return The dialog as Optional String
	 */
	public static Optional<String> processMessage(List<Integer> promptTokens ) {
 		IntTensor retTokens = IntTensor.allocate(Llama3.options.getMaxTokens());
 		int tokNum;
        List<ChatFormat.Message> dialog = new ArrayList<ChatFormat.Message>();
        String userText = DeviceManager.decode(promptTokens);
        ChatFormat.Message promptMessage = new ChatFormat.Message(ChatFormat.Role.USER, userText);
        dialog.add(promptMessage);
        StringTensor p = ChatFormat.extractDialogPrompt(true, dialog);
        if(DEBUG)
        	log.info("ModelRunner.processMessage sending dialog to inference:"+p);
		//try(Timer _ = Timer.log("run model interactive")) {
			tokNum = DeviceManager.runModelTokenize(p, Llama3.options.temperature(), Llama3.options.minp(), Llama3.options.topp(), retTokens);
			if(DEBUG)
				log.info("Returned Tokens="+tokNum);
		//}
		if(tokNum == -1) {
			log.error("Context length exceeded, exiting");
			return Optional.empty();
		}
		List<Integer> retTokenList = retTokens.toList();
		String cleanString = DeviceManager.decode(retTokenList);
		if(DEBUG)
			log.info("Raw returned prompt len="+cleanString.length());
		cleanString = cleanString.trim();
		if(DEBUG)
			log.info("trimmed prompt="+cleanString+(cleanString.length() == 0 ? "..0 len returning Optional.empty()" : cleanString.length()));
		if(cleanString.length() == 0)
			return Optional.empty();
        ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, cleanString);
		return Optional.ofNullable(responseMessage.applyChatTemplate());
	}
	/**
	 * Process the given interaction using the role provided, beginning with model.CreateNewState
	 * and ending with a check for response.isPresent and if so, relatrixLSH.addInteraction, then messageQueue.addLastWait(response).
	 * @param message The message to process, the response is in ChatFormat.Role.ASSISTANT
	 * @param role the role context. role is ChatFromat.Role.USER, ChatFromat.Role.SYSTEM, ChatFromat.Role.ASSISTANT
	 */
	private void processRole(String message, ChatFormat.Role role) {
		if(message.trim().length() == 0) {
			if(DEBUG)
				log.info(role+" message empty...");
			return;
		}
		ChatFormat.Message chatMessage = new ChatFormat.Message(role, message);
		List<ChatFormat.Message> responses = null;
		try {
			responses = relatrixLSH.findNearest(chatMessage);
		} catch (IllegalArgumentException | ClassNotFoundException | IllegalAccessException | IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
			responses = new ArrayList<ChatFormat.Message>();
		}
		if(DEBUG) {
			StringBuilder sb = new StringBuilder("Responses:\n");
			for(int i = 0; i < responses.size(); i++) {
				sb.append(i);
				sb.append(".) ");
				sb.append(responses.get(i));
				sb.append("\n");
			}
			log.info(sb.toString());
		}
		responses.add(chatMessage);
		Optional<String> response = processMessage(ChatFormat.encodeDialogPrompt(true, responses));
		if(response.isPresent() && response.get().trim().length() > 0) {
			if(DEBUG)
				log.info("***Queueing from role USER:"+response.get());
			ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, response.get());
			relatrixLSH.addInteraction(System.currentTimeMillis(), role, chatMessage.encode(), responseMessage.encode());
			try {
				messageQueue.addLastWait(response.get());
			} catch(InterruptedException ie) {}
		}
		//try(Timer _ = Timer.log("reset context")) {
			if(onceThrough)
				DeviceManager.resetContext();
			onceThrough = true;
		//}
	}
	/**
	 * Intercept the model output and generate a movement command to be published to the {@link com.neocoretechs.robocore.propulsion.MotionController}
	 * @param modelOutput
	 * @param currentHeading
	 * @return
	 */
	public Optional<ComeToHeadingStamped> intercept(String modelOutput, float currentHeading) {
		try {
			JSONObject obj = new JSONObject(modelOutput);
			String actionStr = obj.getString("action");
			ComeToHeadingStamped.action act = ComeToHeadingStamped.action.valueOf(actionStr);
			int distance = obj.optInt("distance", 0);
			float heading = obj.optFloat("heading", currentHeading);
			long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
			ComeToHeadingStamped cths = new ComeToHeadingStamped();
			cths.fromJSON(actionStr);
			std_msgs.Int32 mdist = new std_msgs.Int32();
			mdist.setData(distance);
			std_msgs.Float32 mhead = new std_msgs.Float32();
			mhead.setData(heading);
			std_msgs.UInt64 mtime = new std_msgs.UInt64();
			mtime.setData(timestamp);
			return Optional.of(new ComeToHeadingStamped(act,mdist,mhead,mtime));
		} catch (Exception e) {
			log.error("Failed to parse model output: " + e.getMessage());
			return Optional.empty();
		}
	}

}


