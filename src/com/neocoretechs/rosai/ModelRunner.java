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

import jdk.incubator.vector.*;
import stereo_msgs.StereoImage;
import trajectory_msgs.ComeToHeadingStamped;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Externalizable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.Serializable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
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
import com.neocoretechs.relatrix.key.NoIndex;
import com.neocoretechs.relatrix.parallel.SynchronizedThreadManager;
import com.neocoretechs.relatrix.Result;
import com.neocoretechs.relatrix.Relation;
import com.neocoretechs.rocksack.TransactionId;
import com.neocoretechs.rosai.ffi.NativeLoader;
import com.neocoretechs.rosai.relatrix.RelatrixLSH;

import diagnostic_msgs.DiagnosticStatus;
import diagnostic_msgs.KeyValue;

import com.neocoretechs.rocksack.Alias;
import com.neocoretechs.relatrix.DuplicateKeyException;

public class ModelRunner extends AbstractNodeMain {
	private static final Log log = LogFactory.getLog(ModelRunner.class);
	// Batch-size used in prompt evaluation.
	public final static boolean DEBUG = false;
	public static boolean DISPLAY_METADATA = false;
	AsynchRelatrixClientTransaction dbClient = null;
	//static RelatrixTransaction dbClient = null;
	TransactionId xid = null;
	Alias tensorAlias = null;
	// metadata dump
	public static BufferedWriter outputStream = null;
	public static PrintWriter output = null;
	public static FileWriter fileWriter = null;

	PromptFrame promptFrame = null;
	public static final String SYSTEM_PROMPT = "/system_prompt";
	public static final String USER_PROMPT = "/user_prompt";
	public static final String ASSIST_PROMPT = "/assist_prompt";
	public static final String LLM = "/model";

	CircularBlockingDeque<String> messageQueue = new CircularBlockingDeque<String>(1024);

	protected Object mutex = new Object();
	protected CountDownLatch modelLatch = new CountDownLatch(1);
	protected CountDownLatch dbLatch = new CountDownLatch(1);

	static long MESSAGE_THRESHOLD = 5000; // ms minimum between subscribed message reception
	static long lastImageTime = System.currentTimeMillis();

	static RelatrixLSH relatrixLSH = null;
	static ChatFormat chatFormat;

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
	 * Parse the command line for url and xpath directive
	 * @param urlc array of cmdl args, link at 0
	 * @return The Element that matches directive
	 */
	private static Element parseLinks(String[] urlc) {
		//try {	
		Document doc = null;
		if(urlc == null || urlc.length < 2)
			return null;
		try {
			doc = Jsoup.connect(urlc[0])
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
		results = doc.selectXpath(urlc[1]);
		if(results == null)
			return null;
		result = results.first();
		if(result == null)
			return null;
		if(result.is("a"))
			return parseLinks(new String[] {result.attr("href"),"//a"});
		return result;
		//System.out.printf("toString:%s text:%s wholeText:%s%n", result.toString(),result.text(),result.wholeText());
		//System.out.printf("result is a:%b result is a[href]:%b%n",result.is("a"),result.is("a[href]"));
		//} catch(MalformedURLException e) {
		//	e.printStackTrace();
		//}
		//return null;
	}

	/**
	 * element 0 is command <br> /recalltime 
	 * arg day time to end day time
	 * @param query the command line with command times
	 * @return String of Result instances from db that contain 2 elements of question/answer string in time range
	 */
	private static String parseTime(String[] query) {
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
		SynchronizedThreadManager.getInstance().init(new String[] {"LLM","DB"});
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
		// Extract the command line options and parse them into the model options class
		//
		List<String> nodeArgs = connectedNode.getNodeConfiguration().getCommandLineLoader().getNodeArguments();
		//System.out.println("Args:"+Arrays.toString(nodeArgs.toArray(new String[nodeArgs.size()])));
		Llama3.options = Options.parseOptions(nodeArgs);

		//
		// NOTE: use options.getMaxTokens() from here on out after we parse metadata, as the maxtokens() value may be -1 indicating metadata
		// contextLength is used for maximum context size. 
		//
		SynchronizedThreadManager.getInstance().spin(new Runnable() {
			@Override
			public void run() {
				NativeLoader.loadMethods();
				StringTensor s = new StringTensor(Llama3.options.modelPath().toString());
				try(Timer _ = Timer.log("load model")) {
					DeviceManager.loadModel(s, Llama3.options.getMaxTokens());
				}
				modelLatch.countDown();
			}	
		},"LLM");
		//
		// Start new thread for balance of model
		//
		SynchronizedThreadManager.getInstance().spin(new Runnable() {
			@Override
			public void run() {
				try {
					modelLatch.await();
				} catch (InterruptedException e) { return; }
				relatrixLSH = new RelatrixLSH(dbClient, Llama3.options.getMaxTokens());
				// Chat format seems solely based on individual model, so we extract a name in model loader from Metada general.name
	
				// set up the preamble system directives
				promptFrame = new PromptFrame(chatFormat);
				List<Integer> promptTokens = new ArrayList<>();
				promptTokens.add(chatFormat.getBeginOfText());
				List<ChatFormat.Message> prompts = SystemPrompts.getSystemMessages();
				promptTokens.addAll(chatFormat.encodeDialogPrompt(true, prompts));
				Optional<String> response = processMessage(promptTokens);
				if(response.isPresent()) {
					if(DEBUG)
						log.info("***Queueing from system preamble:"+response.get());
					ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, response.get());
					PromptFrame responseFrame = new PromptFrame(chatFormat);
					responseFrame.setMessage(responseMessage);
					List<Integer> responseTokens = (List<Integer>)responseFrame.getRawTokens();
					relatrixLSH.addInteraction(System.currentTimeMillis(), ChatFormat.Role.SYSTEM, promptTokens, responseTokens);
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
						SystemPrompts.frontloadDb(relatrixLSH, chatFormat, fileName+".txt");
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

	public static Optional<String> processMessage(List<Integer> promptTokens ) {
		Set<Integer> stopTokens = chatFormat.getStopTokens();
		//List<Integer> responseTokens = Llama3.generateTokens(0, promptTokens, stopTokens, Llama3.options.getMaxTokens(), Llama3.options.echo(), null);
 		IntTensor retTokens = IntTensor.allocate(Llama3.options.getMaxTokens());
 		int tokNum;
        List<ChatFormat.Message> dialog = new ArrayList<ChatFormat.Message>();
        String userText = DeviceManager.decode(promptTokens);
        ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.USER, userText);
        dialog.add(responseMessage);
        StringTensor p = chatFormat.extractDialogPrompt(true, dialog);
		try(Timer _ = Timer.log("run model interactive")) {
			tokNum = DeviceManager.runModelTokenize(p, Llama3.options.temperature(), Llama3.options.minp(), Llama3.options.topp(), retTokens);
			System.out.println("Returned Tokens="+tokNum);
		}
		if(tokNum == -1) {
			log.error("Context length exceeded, exiting");
			return Optional.empty();
		}
		StringTensor toks = new StringTensor(new byte[Llama3.options.getMaxTokens()]);
		int strLen = DeviceManager.tokenToString(retTokens, tokNum, toks);
		System.out.println("returned prompt len="+strLen);
		System.out.println(toks.toString().substring(0,strLen));
        responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, toks.toString().substring(0,strLen));
        List<Integer> responseTokens = retTokens.toList();
		if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
			responseTokens.removeLast();
		}
		return Optional.ofNullable(DeviceManager.decode(responseTokens));
	}
	/**
	 * Process the given interaction using the role provided, beginning with model.CreateNewState
	 * and ending with a check for response.isPresent and if so, relatrixLSH.addInteraction, then messageQueue.addLastWait(response).
	 * @param message The message to process, the response is in ChatFormat.Role.ASSISTANT
	 * @param role the role context. role is ChatFromat.Role.USER, ChatFromat.Role.SYSTEM, ChatFromat.Role.ASSISTANT
	 */
	private void processRole(String message, ChatFormat.Role role) {
		List<Integer> promptTokens = new ArrayList<>();
		promptTokens.add(chatFormat.getBeginOfText());
		ChatFormat.Message chatMessage = new ChatFormat.Message(role, message);
		promptFrame.setMessage(chatMessage);
		List<Integer> userMessage = new ArrayList<Integer>(promptFrame.getRawTokens());
		List<ChatFormat.Message> responses = null;
		try {
			responses = relatrixLSH.findNearest(promptFrame);
		} catch (IllegalArgumentException | ClassNotFoundException | IllegalAccessException | IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
			responses = new ArrayList<ChatFormat.Message>();
		}
		promptTokens.addAll(chatFormat.encodeDialogPrompt(true, responses));
		if(DEBUG)
			log.info("***User FindNearest returned:"+ DeviceManager.decode(promptTokens));
		Optional<String> response = processMessage(promptTokens);
		if(response.isPresent()) {
			if(DEBUG)
				log.info("***Queueing from role USER:"+response.get());
			ChatFormat.Message responseMessage = new ChatFormat.Message(ChatFormat.Role.ASSISTANT, response.get());
			PromptFrame responseFrame = new PromptFrame(chatFormat);
			responseFrame.setMessage(responseMessage);
			List<Integer> responseTokens = (List<Integer>)responseFrame.getRawTokens();
			relatrixLSH.addInteraction(System.currentTimeMillis(), role, userMessage, responseTokens);
			try {
				messageQueue.addLastWait(response.get());
			} catch(InterruptedException ie) {}
		}
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


