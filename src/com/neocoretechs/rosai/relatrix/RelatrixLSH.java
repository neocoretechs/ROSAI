package com.neocoretechs.rosai.relatrix;

import java.io.IOException;
import java.io.Serializable;
import java.lang.foreign.MemorySegment;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.neocoretechs.relatrix.DuplicateKeyException;
import com.neocoretechs.relatrix.Relation;
import com.neocoretechs.relatrix.type.RelationList;
import com.neocoretechs.relatrix.Result;
import com.neocoretechs.relatrix.client.asynch.AsynchRelatrixClientTransaction;
import com.neocoretechs.relatrix.key.NoIndex;
import com.neocoretechs.rocksack.TransactionId;

import com.neocoretechs.rosai.lsh.CosineHash;
import com.neocoretechs.rosai.FloatTensor;
import com.neocoretechs.rosai.Parallel;
import com.neocoretechs.rosai.TimestampRole;
import com.neocoretechs.rosai.ChatFormat;
import com.neocoretechs.rosai.ChatFormat.Message;
import com.neocoretechs.rosai.ChatFormat.Role;
import com.neocoretechs.rosai.F32FloatTensor;
/**
 * An {@link Index} contains one or more locality sensitive hash tables. These hash
 * tables contain the mapping between a combination of a number of hashes
 * (encoded using an integer) and a list of possible nearest neighbors.<p>
 *
 * A hash function can hash a vector of arbitrary dimensions to an integer
 * representation. The hash function needs to be locality sensitive to work in
 * the locality sensitive hash scheme. Meaning that vectors that are 'close'
 * according to some metric have a high probability to end up with the same
 * hash.<p>
 * In the context of Locality-Sensitive Hashing (LSH), w represents the bucket width or window size.<p>
 * When we compute the hash value for a vector using a random projection. Here's what each component does:<p>
 * vector.dot(randomProjection): Computes the dot product of the input vector and a random projection vector. <br>
 * This projects the input vector onto a random direction.<br>
 * offset: Adds a random offset to the projected value. <br>
 * This helps to shift the projected values and create a more uniform distribution.<p>
 * w: The bucket width or window size. This value determines the granularity of the hash function.<br>
 * By dividing the projected value (plus offset) by w, you're essentially:<br>
 * Quantizing the projected values into discrete buckets.<br>
 * Assigning each bucket a unique hash value. <br>
 * The choice of w affects the trade-off between:<br>
 * Precision: Smaller w values result in more precise hashing, but may lead to more collisions.
 * Larger w values result in fewer collisions, but may reduce precision.<p>
 * In general, w is a hyperparameter that needs to be tuned for specific applications and datasets. 
 * A good choice of w can significantly impact the performance of the LSH algorithm.<p>
 * This class is designed to be stored in the Relatrix database to serve as a template for encoding and retrieving
 * a given set of floating point tensors.<p>
 * add(normalize(tokenList));
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2025
 */
public final class RelatrixLSH implements Serializable, Comparable {
	private static final Log log = LogFactory.getLog(RelatrixLSH.class);
	private static final long serialVersionUID = -5410017645908038641L;
	private static boolean DEBUG = true;
	public int numberOfHashTables = 16;
	public int numberOfHashes = 12;
	public AsynchRelatrixClientTransaction dbClient;
	private TransactionId xid;
	private int maxTokens;
	private static final float maxTokenOverhead = .30f;
	private static final int MAX_DB_CONTENT_SIZE = 2000;
	private static AtomicLong uniTime = new AtomicLong(System.currentTimeMillis());
	/**
	 * Contains the mapping between a combination of a number of hashes (encoded
	 * using an integer) and a list of possible nearest neighbors
	 */
	private List<CosineHash[]> hashTable;
	private UUID key;
	
	static record NearResult(int tokenSize,Result result) {
	}
	
	private static class ThetaPair {
	    final double theta;      // angle in radians (smaller = more similar)
	    final NearResult near;
	    final int index;         // original index for deterministic tie-break
	    ThetaPair(double theta, NearResult near, int index) {
	        this.theta = theta; this.near = near; this.index = index;
	    }
	}

	public RelatrixLSH() {}

	public RelatrixLSH(AsynchRelatrixClientTransaction dbClient, int maxTokens) {
		this(dbClient, 12, 16, 50, maxTokens);
	}
	/**
	 * Initialize a new hash table, uses Cosine hash family.
	 * @param dbClient the Relatrix client from connected node
	 * @param numberOfHashes The number of hash functions that should be used.
	 * @param numberOfhashTables the number of tables each containing number of hashes
	 * @param projectionVectorSize The number of elements in the vector projected into high dimensional space
	 */
	public RelatrixLSH(AsynchRelatrixClientTransaction dbClient, int numberOfHashes, int numberOfHashTables, int projectionVectorSize, int maxTokens) {
		this.dbClient = dbClient;
		this.numberOfHashes = numberOfHashes;
		this.numberOfHashTables = numberOfHashTables;
		this.key = UUID.randomUUID();
		this.hashTable = new ArrayList<CosineHash[]>();
		this.maxTokens = maxTokens - (int)((float)maxTokens*maxTokenOverhead);
		for(int i = 0; i < numberOfHashTables; i++) {
			final CosineHash[] cHash = new CosineHash[numberOfHashes];
			this.hashTable.add(cHash);
			if(numberOfHashes > 64)
				Parallel.parallelFor(0, numberOfHashes, j -> {
					cHash[j] = new CosineHash(projectionVectorSize);
				});
			else
				for(int j = 0; j < numberOfHashes; j++)
					cHash[j] = new CosineHash(projectionVectorSize);
		}
		xid = dbClient.getTransactionId();
	}

	public UUID getKey() {
		return key;
	}
	/**
	 * Keep a global, monotonically incrementing time value that is synchronized with global time in milliseconds.
	 * @return The new incremented time, which may reflect current time if the value would be less than current time, or more than current if 2 calls would overlap.
	 */
	private static long newUniTime() {
		long retTime = uniTime.incrementAndGet();
		if(retTime < System.currentTimeMillis()) {
			uniTime.set(System.currentTimeMillis());
			retTime = uniTime.get();
		}
		return retTime;
	}
	/**
	 * Query the hash table for a vector. It calculates the hash for the vector,
	 * and does a lookup in the hash table. If no candidates are found, an empty
	 * list is returned, otherwise, the list of candidates is returned.
	 * 
	 * @param query The query vector.
	 * @return Does a lookup in the table for a query using its hash. If no
	 *         candidates are found, an empty list is returned, otherwise, the
	 *         list of candidates is returned as List<Result> where Result contains timetamp, NoIndex with Message
	 * @throws IOException asynchronous database client exception
	 * @throws IllegalAccessException asynchronous database client exception
	 * @throws ClassNotFoundException asynchronous database client exception
	 * @throws IllegalArgumentException asynchronous database client exception
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception
	 */
	public List<Result> query(List<Integer> query) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		ArrayList<Result> res = new ArrayList<Result>();
		for(int i = 0; i < hashTable.size(); i++) {
			Integer combinedHash = hash(hashTable.get(i), normalize(query));
			//if(DEBUG)
			//	log.info("Querying combined hash for query "+i+" of "+hashTable.size()+":"+combinedHash);
			CompletableFuture<Iterator> cit = dbClient.findSet(xid, combinedHash, '?', '?');
			Iterator<?> it = cit.get();
			//int cnt = 0;
			while(it.hasNext()) {
				Result r = (Result) it.next();
				// should be NoIndex values
				res.add(r);
				//System.out.print(++cnt+"\r");
			}
			//System.out.println();
		}
		return res;
	}

	/**
	 * Perform a parallel Relatrix query on the normalized FloatTensor of untemplated content.<p>
	 * The query will be by LSH index, returning TimestampRole and content String
	 * @param normalizedQuery FloatTensor of bare toeksn which will produce a LSH hash key to search by
	 * @return List or Result result set from Relatrix element 0 original LSH, element 1 TimestampRole, element 2, content string
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public List<Result> queryParallel(FloatTensor normalizedQuery) throws InterruptedException, ExecutionException  {
		ArrayList<Object> iq = new ArrayList<Object>();
		for(int i = 0; i < hashTable.size(); i++) {
			Integer combinedHash = hash(hashTable.get(i), normalizedQuery);
			iq.add(combinedHash);
		}
		return queryParallel(iq);
	}
	/**
	 * Query the hash table in parallel for a vector of LSH indexes. The query has the indexes,
	 * and uses these directly to do a lookup. If no candidates are found, an empty
	 * list is returned, otherwise, the list of candidates is returned.
	 * 
	 * @param query The query vector.
	 * @return Does a lookup in the table for a query using its hash. If no
	 *         candidates are found, an empty list is returned, otherwise, the
	 *         list of candidates is returned as List<Result> where Result contains TimestampRole, content String
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception
	 */
	public List<Result> queryParallel(List<Object> query) throws InterruptedException, ExecutionException  {
		List<Result> res = null;
		//try (var _ = Timer.log("Querying combined hash for List of "+query.size())) {
			CompletableFuture<List> cres = dbClient.findSetParallel(xid, query, '?', '?');
			res = cres.get();
		//}
		return res;
	}
	/**
	 * Perform a parallel query on the map values which in our context are TimestampRole instances
	 * @param query The List of instances to query
	 * @return the Result instances of result set containing original TimestampRole and Message
	 * @throws IllegalArgumentException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public List<Result> queryParallelMap(List<Object> query) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		List<Result> res = null;
		//try (var _ = Timer.log("Querying combined hash for List of "+query.size())) {
			CompletableFuture<List> cres = dbClient.findSetParallel(xid, '*', query, '?');
			res = cres.get();
			//if(DEBUG)
			//	for(Result r: res)
			//		System.out.println(((TimestampRole)(r.get(0))).getTimestamp()+" "+r);
		//}
		return res;
	}
	/**
	 * Normalizes integer tokens into a zero-centered, unit-length float tensor
	 * for cosine similarity use with Gaussian random projection.
	 * @param tokens List of tokenized values
	 * @return FloatTensor normalized to unit length zero-centered mean
	 */
	public static FloatTensor normalize(List<Integer> tokens) {
		int size = tokens.size();
		float[] floats = new float[size];
		// Cast tokens to float and compute mean
		float mean = 0.0f;
		for (int i = 0; i < size; i++) {
			float value = (float) tokens.get(i);
			floats[i] = value;
			mean += value;
		}
		mean /= size;
		// Zero-center
		for (int i = 0; i < size; i++) {
			floats[i] -= mean;
		}
		// Unit-length normalization
		float norm = 0.0f;
		for (float f : floats) {
			norm += f * f;
		}
		norm = (float) Math.sqrt(norm);
		if (norm != 0f) {
			for (int i = 0; i < size; i++) {
				floats[i] /= norm;
			}
		}
		return new F32FloatTensor(size, MemorySegment.ofArray(floats));
	}
	/**
	 * Add the user/assistant interaction. Generates either commit or rollback on duplicate key.
	 * @param chatFormat 
	 * @param initiator the initiator of the interaction; either USER or SYSTEM
	 * @param invocation
	 * @param response response
	 */
	public void addInteraction(ChatFormat chatFormat, ChatFormat.Message invocation, ChatFormat.Message response) {
		List<ChatFormat.Message[]> segmentedContent = segmentContent(invocation, response);
		if(DEBUG) {
			log.info("$$$ Segmented content size="+segmentedContent.size());
			for(int i = 0; i < segmentedContent.size(); i++) {
				log.info(i+".) A="+segmentedContent.get(i)[0].content()+"\r\nB="+segmentedContent.get(i)[1].content());
			}
		}
		for(ChatFormat.Message[] segments : segmentedContent) {
			//
			// add user/asst
			//
			long baseTime = newUniTime();
			FloatTensor fvec = normalize(chatFormat.stripFormatting(chatFormat.encodeAsList(segments[0].content())));
			FloatTensor fvecA = normalize(chatFormat.stripFormatting(chatFormat.encodeAsList(segments[1].content())));
			for(int i = 0; i < hashTable.size(); i++) {
				Integer combinedHash = hash(hashTable.get(i), fvec);
				Integer combinedHashA = hash(hashTable.get(i), fvecA);
				TimestampRole tr_user = new TimestampRole(baseTime, segments[0].role());
				TimestampRole tr_assistant = new TimestampRole(baseTime, ChatFormat.Role.ASSISTANT);
				NoIndex noIndex = NoIndex.create(segments[0]);
				NoIndex noIndexA = NoIndex.create(segments[1]);
				try {
					CompletableFuture<Relation> res = dbClient.store(xid, combinedHash, tr_user, noIndex);
					CompletableFuture<Relation> resA = dbClient.store(xid, combinedHashA, tr_assistant, noIndexA);
					Relation rel1 = res.get();
					Relation rel2 = resA.get();
					if(rel1 != null && rel2 != null) // may never return null, but check anyway
						dbClient.commit(xid); // Only after both store ops succeed
					else
						dbClient.rollback(xid);
					//if(DEBUG) {
					//	log.info("### Successful store:"+combinedHash+" "+tr_user+" "+segments[0].content().length()+"\r\n"+
					//			combinedHashA+" "+tr_assistant+" "+segments[1].content().length());
					//}
				} catch (InterruptedException | ExecutionException e) {
					//if(DEBUG)
					//	log.info("@@@ Failed store:"+combinedHash+" "+tr_user+" "+segments[0].content().length());
					dbClient.rollback(xid);
				}
			}
		}
	}	
	
	/**
	 * Segment the content into chunks of monotonically increasing TimestampRole to allow more progressive increase of context size
	 * the recursively call addInteraction until size decreases below threshold
	 * @param message original message
	 * @return final message chunk to store as original interaction element
	 */
	private List<ChatFormat.Message[]> segmentContent(Message message, Message message2) {
		List<ChatFormat.Message[]> ret = new ArrayList<ChatFormat.Message[]>();
		String remain = message.content();
	    if (remain == null || remain.isEmpty()) 
	    	return ret;
    	ChatFormat.Message[] retEntry = new ChatFormat.Message[2];
    	int indexA = 0;
	    while (!remain.isEmpty()) {
	    	if(indexA == 2) {
	    		ret.add(retEntry);
	    		retEntry = new ChatFormat.Message[2];
	    		indexA = 0;
	    	}
	        int maxLen = Math.min(MAX_DB_CONTENT_SIZE, remain.length());
	        // find last delimiter within the first maxLen chars
	        int splitPos = -1;
	        String slice = remain.substring(0, maxLen);
	        if(maxLen < MAX_DB_CONTENT_SIZE) {
	            ChatFormat.Message newMessage;
	            if(indexA == 0)
	            	newMessage = new ChatFormat.Message(message.chatFormat(), message.role(), slice);
	            else
	            	newMessage = new ChatFormat.Message(message.chatFormat(), ChatFormat.Role.ASSISTANT, slice);
		        retEntry[indexA++] = newMessage;
		        break;
	        }
	        // prefer sentence end, then CR, then space
	        splitPos = slice.lastIndexOf('.');
	        if (splitPos == -1) splitPos = slice.lastIndexOf('\r');
	        if (splitPos == -1) splitPos = slice.lastIndexOf(' ');
	        // if no delimiter found and the message is longer than maxLen, split at maxLen
	        if (splitPos == -1) {
	            if (remain.length() > maxLen) {
	                splitPos = maxLen; // split at maxLen (will use substring(0, maxLen))
	            } else {
	                splitPos = remain.length(); // take the rest
	            }
	        } else {
	            // include the delimiter in the segment (optional). If you don't want it, use splitPos instead of splitPos+1
	            splitPos = splitPos + 1;
	        }
	        // guard: ensure splitPos is within bounds
	        if (splitPos <= 0) {
	            // nothing to extract safely; break to avoid infinite loop
	            break;
	        }
	        if (splitPos > remain.length()) 
	        	splitPos = remain.length();
	        String part = remain.substring(0, splitPos).trim();
	        // update remain safely
	        remain = (splitPos >= remain.length()) ? "" : remain.substring(splitPos);
	        if (part.isEmpty()) {
	            // continue loop; but ensure remain is shrinking (it is)
	            continue;
	        }
	        ChatFormat.Message newMessage;
            if(indexA == 0)
            	newMessage = new ChatFormat.Message(message.chatFormat(), message.role(), part);
            else
            	newMessage = new ChatFormat.Message(message.chatFormat(), ChatFormat.Role.ASSISTANT, part);
	        retEntry[indexA++] = newMessage;
	        if(DEBUG)
	        	log.info("segmentContent splitPos:"+splitPos+" part:"+part+" remain:"+remain);
	    }
	    // next message
	    remain = message2.content();
	    while (!remain.isEmpty()) {
	    	if(indexA == 2) {
	    		ret.add(retEntry);
	    		retEntry = new ChatFormat.Message[2];
	    		indexA = 0;
	    	}
	        int maxLen = Math.min(MAX_DB_CONTENT_SIZE, remain.length());
	        // find last delimiter within the first maxLen chars
	        int splitPos = -1;
	        String slice = remain.substring(0, maxLen);
	        if(maxLen < MAX_DB_CONTENT_SIZE) {
	        	if(indexA == 1) {
	        		ChatFormat.Message newMessage = new ChatFormat.Message(message.chatFormat(), ChatFormat.Role.ASSISTANT, slice);
	        		retEntry[indexA++] = newMessage;
	        		ret.add(retEntry);
	        		break;
	        	}
	        }
	        // prefer sentence end, then CR, then space
	        splitPos = slice.lastIndexOf('.');
	        if (splitPos == -1) splitPos = slice.lastIndexOf('\r');
	        if (splitPos == -1) splitPos = slice.lastIndexOf(' ');
	        // if no delimiter found and the message is longer than maxLen, split at maxLen
	        if (splitPos == -1) {
	            if (remain.length() > maxLen) {
	                splitPos = maxLen; // split at maxLen (will use substring(0, maxLen))
	            } else {
	                splitPos = remain.length(); // take the rest
	            }
	        } else {
	            // include the delimiter in the segment (optional). If you don't want it, use splitPos instead of splitPos+1
	            splitPos = splitPos + 1;
	        }
	        // guard: ensure splitPos is within bounds
	        if (splitPos <= 0) {
	            // nothing to extract safely; break to avoid infinite loop
	            break;
	        }
	        if (splitPos > remain.length()) 
	        	splitPos = remain.length();
	        String part = remain.substring(0, splitPos).trim();
	        // update remain safely
	        remain = (splitPos >= remain.length()) ? "" : remain.substring(splitPos);
	        if (part.isEmpty()) {
	            // continue loop; but ensure remain is shrinking (it is)
	            continue;
	        }
	        ChatFormat.Message newMessage;
	        if(indexA == 0)
	        	newMessage = new ChatFormat.Message(message.chatFormat(), ChatFormat.Role.USER, part);
	        else
	        	newMessage = new ChatFormat.Message(message.chatFormat(), ChatFormat.Role.ASSISTANT, part);
	        retEntry[indexA++] = newMessage;
	        if(DEBUG)
	        	log.info("segmentContent splitPos:"+splitPos+" part:"+part+" remain:"+remain);
	    }
	    // have to fill array
	    if(indexA == 1) {
	        int splitPos = retEntry[0].content().lastIndexOf('.');
	        if (splitPos == -1) splitPos = retEntry[0].content().lastIndexOf('\r');
	        if (splitPos == -1) splitPos = retEntry[0].content().lastIndexOf(' ');
	        if (splitPos == -1) splitPos = retEntry[0].content().length()/2;
	        ChatFormat.Message newA = new ChatFormat.Message(retEntry[0].chatFormat(), ChatFormat.Role.USER, retEntry[0].content().substring(0,splitPos));
	        ChatFormat.Message newB = new ChatFormat.Message(retEntry[0].chatFormat(), ChatFormat.Role.ASSISTANT, retEntry[0].content().substring(splitPos));
	        retEntry[0] = newA;
	        retEntry[1] = newB;
	        ret.add(retEntry);
	        if(DEBUG)
	        	log.info("segmentContent fill missing ASSISTANT:"+splitPos+" part:"+newA+" remain:"+newB);
	    }
	    return ret;
	}
	/**
	 * Find the nearest candidates using theta angle similarity. This is the arc cosine of the
	 * standard cosine similarity.<p> The pipe line is as follows:<br>
	 * Tokenize prompt, normalize token vector, perform parallel query on LSH
	 * indexes of normalized vector<br>
	 * If no result sets come back in thetaNearestResults, get the latest timestamp, retrieve that LSH index
	 * and perform a parallel query for those LSH indexes.<br>
	 * If thetaNearestResults is still empty, theres nothing to work with, so load orignal prompt and return early.<br>
	 * Build a map of thetaNearestResults that has Result(s) from the last series of TimestampRole query, 
	 * TimestampRole LSH index, and/or original message.<br>
	 * find similar relevant entries via cosine similarity and theta similarity in cosDist.<br>
	 * Build a TreeMap in descending order of cosDist, index into thetaNearestResults<br>
	 * flatten to list of descending order <br>
	 * Walk the list of interactions and and create an insertMap of missing interaction elements.<br>
	 * insertMap is indexes into thetaNearestResults with complimentary TimestampRole-Message<br>
	 * Now we have our insertMap, so process those performing another parallel query to locate content of missing elements.<br>
	 * Try and resolve associated interaction elements that are missing and then interpose those with our
	 * nearest list, if found. If we cant locate a corresponding interaction element, then skip the entry.<br>
	 * Our map has a TimestampRole with a matching timestamp and the opposite role used to launch the query.<br>
	 * @param promptFrame list of messages to populate starting with initial request
	 * @return List of retrieved messages
	 * @throws ExecutionException asychronous database client exception
	 * @throws InterruptedException asychronous database client exception
	 * @throws IOException asychronous database client exception
	 * @throws IllegalAccessException asychronous database client exception
	 * @throws ClassNotFoundException asychronous database client exception
	 * @throws IllegalArgumentException asychronous database client exception
	 */
	public List<ChatFormat.Message> findNearest(ChatFormat.Message promptFrame) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		List<Result> thetaNearestResults = null;
		int contentSize = promptFrame.chatFormat().length(promptFrame.encode());
		List<Integer> promptFrameTokens = promptFrame.chatFormat().stripFormatting(promptFrame.chatFormat().encodeAsList(promptFrame.content()));
		//if(DEBUG)
		//	log.info("findNearest User query has "+promptFrameTokens.size()+" tokens from "+promptFrame);
		List<ChatFormat.Message> returnMessages = new ArrayList<ChatFormat.Message>();
		FloatTensor fmessage = normalize(promptFrameTokens);
		thetaNearestResults = queryParallel(fmessage);
		if(DEBUG)
			log.info("Retrieved "+thetaNearestResults.size()+" entries from LSH index query.");
		// If we retrieved nothing from semantic query of initial message, try getting last timestamp
		if(thetaNearestResults.isEmpty()) {
			//return results;
			List<Result> resByTime = primeByTime();
			// if we have a list of the timestamped results, get the index from them and
			// retrieve identical indexes that indicate relevance to last timestamped messages
			if(resByTime != null && !resByTime.isEmpty()) {
				ArrayList<Object> lshQuery = new ArrayList<Object>();
				// each timestamp entry
				for(int i = 0; i < resByTime.size(); i++) {
					Result result = resByTime.get(i);
					// LSH at Result.get(0)
					// re-form the nearest list by getting all the LSH for the given timestamp
					if(!lshQuery.contains(result.get(0)))
						lshQuery.add(result.get(0));
				}
				// now query the matching LSH indexes we got from each timestamp
				thetaNearestResults = queryParallel(lshQuery);
				// If thetaNearestResults is still empty, theres nothing to work with, so load original prompt and return early.
				if(thetaNearestResults.isEmpty()) {
					returnMessages.add(promptFrame);
					if(DEBUG)
						log.info("Early return with original prompt...");
					return returnMessages;
				}
			}
		}
		// thetaNearestResults has Result(s) from the last series of TimestampRole query, 
		// TimestampRole LSH index, and/or original message.<p>
		// fmessage is our original message, mormalized as FloatTensor<p>
		// organize our current Results and find similar relevant entries via cosine similarity
		// and theta similarity in cosDist.<p>
		// Build a Map in order of cosDist, index into thetaNearestResults.
		// Compute and keep top K most theta-similar (smallest theta)
		int K = thetaNearestResults.size(); // or limit to top-K if you want
		List<ThetaPair> scored = new ArrayList<>(K);
		for (int i = 0; i < K; i++) {
		    Result thetaNearestResult = thetaNearestResults.get(i);
		    Message nearestMessage = (Message) (((NoIndex) thetaNearestResult.get(2)).getInstance());
		    List<Integer> restensor = promptFrame.chatFormat().stripFormatting(promptFrame.chatFormat().encodeAsList(nearestMessage.content()));
		    FloatTensor cantensor = normalize(restensor);
		    // compute dot safely for the overlapping length
		    int n = Math.min(fmessage.size(), cantensor.size());
		    double dot = 0.0;
		    for (int j = 0; j < n; j++) {
		        dot += fmessage.getFloat(j) * cantensor.getFloat(j);
		    }
		    // clamp to avoid NaN from acos
		    dot = Math.max(-1.0, Math.min(1.0, dot));
		    double theta = Math.acos(dot); // angle in radians, smaller = more similar
		    int encodedLen = promptFrame.chatFormat().length(nearestMessage.encode());
		    NearResult nr = new NearResult(encodedLen, thetaNearestResult);
		    scored.add(new ThetaPair(theta, nr, i));
		}
		// stable sort by theta ascending (most similar first), tie-break by original index
		scored.sort((a, b) -> {
		    int c = Double.compare(a.theta, b.theta); // ascending
		    if (c != 0) return c;
		    return Integer.compare(a.index, b.index);
		});
		// flatten to list of NearResult in most-similar-first order
		List<NearResult> valueList = scored.stream().map(p -> p.near).collect(Collectors.toList());
		// list of eventual insertion points into valueList
		HashMap<Integer, TimestampRole> insertMap = new HashMap<Integer, TimestampRole>();
		// walk over interactions
		int listCtr = 0;
		while(listCtr < valueList.size()) {
			TimestampRole tsRole = (TimestampRole)valueList.get(listCtr).result().get(1);
			switch(tsRole.getRole()) {
			case Role.SYSTEM:
			case Role.USER:
				if(listCtr+1 >= valueList.size() || 
					!(((TimestampRole)(valueList.get(listCtr+1).result().get(1))).getRole().equals(Role.ASSISTANT)) ||
					!(((TimestampRole)(valueList.get(listCtr+1).result().get(1))).getTimestamp().equals(tsRole.getTimestamp()))) {
					TimestampRole tsr = new TimestampRole();
					tsr.setTimestamp(tsRole.getTimestamp());
					tsr.setRole(Role.ASSISTANT);
					insertMap.put(listCtr, tsr); //nearest entry is USER, next is NOT ASSISTANT, so set this to look for ASSISTANT
					//if(DEBUG)
					//	log.info("findNearest role USER insert after "+listCtr+" points to nearest:"+valueList.get(listCtr)+" entry:"+tsRole+" for matching "+tsr);
					// now advance to next entry
					++listCtr;	
				} else
					// advance list by 2, since 'next' entry is valid response of 'ASSISTANT'
					listCtr+=2;
				break;
			case Role.ASSISTANT:
				// role is assistant without previous valid USER or SYSTEM (matching timestamp), otherwise we would have skipped this entry
				TimestampRole tsr = new TimestampRole();
				tsr.setTimestamp(tsRole.getTimestamp());
				tsr.setRole(Role.USER);
				insertMap.put(listCtr, tsr); //nearest entry is ASSISTANT, previous was NOT USER or SYSTEM, so set this to look for USER
				//if(DEBUG)
				//	log.info("findNearest role ASSISTANT insert before "+listCtr+" points to nearest:"+valueList.get(listCtr)+" entry:"+tsRole+" for matching "+tsr);
				++listCtr;
				break;
			default:
				valueList.remove(listCtr);
				log.error("Unknown role encountered");
				break;
			}
		}
		if(DEBUG)
			log.info("insert map size:"+insertMap.size());
		// Now we have our insertMap so process those performing another parallel query
		// to try and resolve associated interaction elements that are missing and then interpose those with our
		// nearest list, if found. If we cant locate a corresponding interaction element, then skip the entry.
		// Our map has a TimestampRole with a matching timestamp and the opposite role used to launch the query.
		List<Object> timestampRoleQuery = new ArrayList<Object>(insertMap.values());
		List<Result> timestampRoleResult = null;
		if(!timestampRoleQuery.isEmpty()) {
			timestampRoleResult = queryParallelMap(timestampRoleQuery);
		}
		if(DEBUG)
			log.info("timestampRoleResult size:"+timestampRoleResult.size());
		// walk the NearResults looking for matching insertMap elements
		// insert Results into original valueList in correct order, calculating size of token list for each new element
		// walk over interactions with our insert map
		listCtr = 0;
		mainLoop:
		while (listCtr < valueList.size()) {
		    TimestampRole tsRole = (TimestampRole)valueList.get(listCtr).result().get(1);
		    switch (tsRole.getRole()) {
		        case Role.SYSTEM:
		        case Role.USER:
		        	TimestampRole insertMapValue = insertMap.get(listCtr);
		        	if(insertMapValue != null) {
		        		// we have an insert, match insert then move on to next entry
		        		Optional<Result> insertMessage = matchTimestamp(timestampRoleResult, insertMapValue);
		        		if(insertMessage.isPresent()) {
		        			Result resultA = insertMessage.get();
		        			Message resultMessageA = (Message) ((NoIndex)(resultA.get(resultA.length()-1))).getInstance();
		        			int tokenSize = promptFrame.chatFormat().length(resultMessageA.encode());
		        			NearResult resultU = valueList.get(listCtr);
		        			Message resultMessageU = (Message) ((NoIndex)(resultU.result.get(resultU.result.length()-1))).getInstance();
		        		    int prospect = contentSize + resultU.tokenSize + tokenSize; // contentSize of prompt way up at the beginning
		        		    if(prospect < maxTokens) {
		        		    	contentSize = prospect;
		        		        returnMessages.add(resultMessageU);
		        		        returnMessages.add(resultMessageA);
		        		    } else
		        		    	break mainLoop;
		        		} else {
		        			// we found an insert map directive, but then no matching database entry to insert, this indicates bad data sequence
		        			log.info(">> DATABASE INCONSISTENCY: NO MATCHING ASSISTANT ENTRY FOR INSERTION INTO DIALOG SEQUENCE:"+insertMapValue);
		        		}
		        	} 
		        	// move on to next entry
		        	++listCtr;
		        	continue;
		        // we should encounter either a user/system to insert, or have a previous user/system entry
		        case Role.ASSISTANT:
		        	insertMapValue = insertMap.get(listCtr);
		        	if (insertMapValue != null) {
		        		Optional<Result> insertMessage = matchTimestamp(timestampRoleResult, insertMapValue);
		        		if (insertMessage.isPresent() ) {
		        			// USER/SYSTEM before ASSISTANT
		        			Result resultU = insertMessage.get();
		        			Message resultMessageU = (Message) ((NoIndex)(resultU.get(resultU.length()-1))).getInstance();
		        			int tokenSize = promptFrame.chatFormat().length(resultMessageU.encode());
		        			NearResult resultA = valueList.get(listCtr);
		        			Message resultMessageA = (Message) ((NoIndex)(resultA.result.get(resultA.result.length()-1))).getInstance();
		        		    int prospect = contentSize + resultA.tokenSize + tokenSize; // contentSize of prompt way up at the beginning
		        		    if(prospect < maxTokens) {
		        		    	contentSize = prospect;
		        		        returnMessages.add(resultMessageU);
		        		        returnMessages.add(resultMessageA);
		        		    } else
		        		    	break mainLoop;
		        		} else {
		        			// we found an insert map directive, but then no matching database entry to insert, this indicates bad data sequence
		        			log.info(">> DATABASE INCONSISTENCY: NO MATCHING USER OR SYSTEM ENTRY FOR INSERTION INTO DIALOG SEQUENCE:"+insertMapValue);
		        		}
		        	// no insert, we have to check valid user/system previous
		        	} else {
		        		if(listCtr == 0) {
		        			log.info(">> DATABASE INCONSISTENCY: NO MATCHING USER OR SYSTEM ENTRY FOR INSERTION INTO DIALOG SEQUENCE:"+insertMapValue);
		        		} else {
		        			TimestampRole tsRolePrev = (TimestampRole)valueList.get(listCtr-1).result().get(1);
		        			if((tsRolePrev.getRole().equals(Role.SYSTEM) || tsRolePrev.getRole().equals(Role.USER)) &&
		        				(tsRolePrev.getTimestamp().equals(tsRole.getTimestamp()))) {
		        				// previous entry is valid USER/SYSTEM before ASSISTANT
			        			Result resultU = valueList.get(listCtr-1).result();
			        			Message resultMessageU = (Message) ((NoIndex)(resultU.get(resultU.length()-1))).getInstance();
			        			int tokenSize = promptFrame.chatFormat().length(resultMessageU.encode());
			        			NearResult resultA = valueList.get(listCtr);
			        			Message resultMessageA = (Message) ((NoIndex)(resultA.result.get(resultA.result.length()-1))).getInstance();
			        		    int prospect = contentSize + resultA.tokenSize + tokenSize; // contentSize of prompt way up at the beginning
			        		    if(prospect < maxTokens) {
			        		    	contentSize = prospect;
			        		        returnMessages.add(resultMessageU);
			        		        returnMessages.add(resultMessageA);
			        		    } else
			        		    	break mainLoop;
		        			} else {
		        				log.info(">> DATABASE INCONSISTENCY: NO MATCHING USER OR SYSTEM ENTRY FOR INSERTION INTO DIALOG SEQUENCE:"+insertMapValue);
		        			}
		        		}     
		        	}
		        	++listCtr;
		        	continue;
		        default:
		            log.error("Unknown role encountered");
		            ++listCtr;
		            continue;
		    }
		} // end while

		// put most recent user query last, we already accounted for context size at start of pipeline
		returnMessages.add(promptFrame);
		printStats(returnMessages);
		return returnMessages;
	}
	
	public static void printStats(List<ChatFormat.Message> returnMessages) {
		if(DEBUG) {
			int ilen = 0;
			log.info("--------------- Number of return messages:"+returnMessages.size()+" ---------------");
			for(int i = 0; i < returnMessages.size(); i++) {
				ilen += returnMessages.get(i).content().length();
				log.info(i+".) length="+returnMessages.get(i).content().length()+" total="+ilen+" - "+returnMessages.get(i));
			}
			log.info("+++++++++++++++ End of "+returnMessages.size()+" return messages +++++++++++++++");
		}
	}

	/**
	 * Match a TimestampRole timestamp with a Result set List element 0 timestamp
	 * @param source
	 * @param target
	 * @return the matching Result set with timetampRole and any additional retrieved elements
	 */
	private static Optional<Result> matchTimestamp(List<Result> source, TimestampRole target) {
		Optional<Result> res = Optional.empty();
		for(Result queryResult: source) {
			TimestampRole timestampQueryResult = (TimestampRole) queryResult.get(0);
			//if(DEBUG)
			//	log.info("findNearest.matchTimestamp timestampQueryResult="+timestampQueryResult+" target="+target+" queryResult="+queryResult);
			//String stime1 = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampQueryResult.getTimestamp()), ZoneId.systemDefault()).toString();
			//String stime2 = LocalDateTime.ofInstant(Instant.ofEpochMilli(target.getTimestamp()), ZoneId.systemDefault()).toString();
			if(timestampQueryResult.getTimestamp().longValue() == target.getTimestamp().longValue()) {
				res = Optional.ofNullable(queryResult);
				break;
			}
		}
		return res;
	}
	/**
	 * Get the NoIndex vector for passed TimestampRole. Format agnostic. 
	 * @param chatFormat chatFormat instance
	 * @param returns running list of ChatFormat.message retrievals
	 * @param trr target TimestampRole - passed on after use in retrieval
	 * @throws InterruptedException asynchronous db client exception
	 * @throws ExecutionException asynchronous client/database exception
	 */
	@SuppressWarnings("unused")
	private void getTimestampRole(ChatFormat chatFormat, List<ChatFormat.Message> returns, TimestampRole trr) throws InterruptedException, ExecutionException {
		//if(DEBUG)
		//	log.info("getTimestampRole for "+trr);
		//CompletableFuture<Stream> cit = dbClient.findStream(xid, '*', trr, '?');
		//cit.get().forEach(e->{
		CompletableFuture<Iterator> cit = dbClient.findSet(xid, '*', trr, '?');
		Iterator<?> it = cit.get();
		// get one instance
		if(it.hasNext()) {
			//if(DEBUG)
				//log.info("getTimeStampRole result");
			//addRetrievedMessage((Result)e, trr, results, returns, tokenizer);
			ChatFormat.Message message = (ChatFormat.Message)((NoIndex)((Result)it.next()).get(0)).getInstance();
			returns.add(message);
			//});
		}
	}
	/**
	 * Append token list to end of full list. If existing token list contains end_of_message, overwrite it and extend.
	 * @param chatFormat
	 * @param tokens
	 * @param results
	 */
	@SuppressWarnings("unused")
	private void appendTokens(ChatFormat chatFormat, List<Integer> tokens, List<Integer> results) {
		if(chatFormat.getStopTokens().contains(results.get(results.size()-1))) {
			results.addAll(results.size()-1, tokens);
		} else {
			results.addAll(tokens);
		}
		if(!chatFormat.getStopTokens().contains(results.get(results.size()-1))) {
			results.add((Integer)chatFormat.getStopTokens().toArray()[0]);
		}
	}
	/**
	 * Prime the semantic pump by retrieving last time value, then the relations with that value, later, feed the
	 * vectors for that time into the prompt, then retrieve any other indexes that match the retrieved indexes.
	 * So should pick up at least the 2 indexes for a USER/ASSISTANT request/response for a given timestamp
	 * @return The Results result set with index at element 0
	 * @throws InterruptedException asynchronous db client exception
	 * @throws ExecutionException asynchronous client/database exception
	 */
	private List<Result> primeByTime() throws InterruptedException, ExecutionException {
		ArrayList<Result> res = new ArrayList<Result>();
		TimestampRole lastTime = (TimestampRole) dbClient.last(xid, TimestampRole.class).get();
		if(lastTime != null) {
			//try (var _ = Timer.log("primeByTime Querying by time "+ LocalDateTime.ofInstant(Instant.ofEpochMilli(lastTime.getTimestamp()), ZoneId.systemDefault()))) {
				CompletableFuture<Iterator> cres = dbClient.findSet(xid, '?', lastTime, '*');
				Iterator<?> it = cres.get();
				while(it.hasNext()) {
					// LSH index in Result(0)
					res.add((Result) it.next());
				}
			//}
		}
		if(DEBUG)
			log.info("primeByTime returned "+res.size()+" results.");
		return res;
	}
	
	public Iterator dump() throws ExecutionException, InterruptedException {
			CompletableFuture<Iterator> cit = dbClient.findSet(xid, '?', '?', '?');
			return cit.get();
	}
	public String dump(Iterator it, ChatFormat chatFormat) throws ExecutionException {
		if(it.hasNext()) {
			Result r = (Result) it.next();
			StringBuilder sb = new StringBuilder();
			sb.append("LSH=");
			sb.append(r.get(0));
			sb.append(" ");
			sb.append("Time/Role=");
			sb.append(r.get(1));
			sb.append("\r\n");
			NoIndex noIndex = (NoIndex) r.get(2);
			sb.append((ChatFormat.Message)noIndex.getInstance());
			//List<Integer> restensor = (List<Integer>)noIndex.getInstance();
			//sb.append(DeviceManager.decode(chatFormat, restensor));
			sb.append("\r\n");
			return sb.toString();
		}
		return null;
	}
	/**
	 * command /recalltime 
	 * arg day time to end day time
	 * @param query the command line with command times, start, end in form DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
	 * @return Iterator of Result instances from db that contain 3 elements of Index, TimestampRole, question/answer Message in time range
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@SuppressWarnings("unused")
	private Iterator dumpTime(ChatFormat chatFormat, String startTime, String endTime) throws InterruptedException, ExecutionException {
		CompletableFuture<Stream> s;
		String tq,tqe;
		LocalDateTime localDateTime;
		long millis,millise;
		// day time to end day time
		localDateTime = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
		millis = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		localDateTime = LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
		millise = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		TimestampRole tStart = new TimestampRole(millis, ChatFormat.Role.ASSISTANT);
		TimestampRole tEnd = new TimestampRole(millise, ChatFormat.Role.USER);
		return dbClient.findSubSet(xid,'?','?','?',Integer.class,Integer.class,tStart,tEnd,NoIndex.class,NoIndex.class).get();
		/*
		s = dbClient.findSubStream(xid,'*','?','?',tStart,tEnd,NoIndex.class,NoIndex.class);
		StringBuilder sb = new StringBuilder();
		try {
			s.get().forEach(e->{
				sb.append(((Result)e).get(0));
				sb.append("\r\n");
				List<Integer> restensor = (List<Integer>)((NoIndex)(((Result)e).get(1))).getInstance();
				sb.append(DeviceManager.decode(chatFormat, restensor));
				sb.append("\r\n");
			});
		} catch(InterruptedException | ExecutionException ie) {}
		return sb.toString();
		*/
	}

	/**
	 * Calculate the combined hash for a vector using CosineHash.
	 * @param hash one of numberOfHashes
	 * @param vector The vector to calculate the combined hash for.
	 * @return An integer representing a combined hash.
	 */
	private Integer hash(CosineHash[] hash, FloatTensor vector){
		int hashes[] = new int[hash.length];
		for(int i = 0 ; i < hash.length ; i++){
			hashes[i] = hash[i].hash(vector);
		}
		Integer combinedHash = CosineHash.combine(hashes);
		return combinedHash;
	}

	/**
	 * Return the number of hash functions used in the CosingHash table.
	 * @return The number of hash functions used in the hash table.
	 */
	public int getNumberOfHashes() {
		return hashTable.get(0).length;
	}

	@Override
	public String toString() {
		return String.format("%s key=%s tables=%d hashes=%d",this.getClass().getName(), key, numberOfHashTables, numberOfHashes);
	}

	@Override
	public int compareTo(Object o) {
		int key0 = key.compareTo(((RelatrixLSH)o).key);
		if(key0 != 0)
			return key0;
		for(int i = 0; i < hashTable.size(); i++) {
			CosineHash[] cos0 = hashTable.get(i);
			CosineHash[] cos1 = (((RelatrixLSH)o).hashTable.get(i));
			for(int j = 0; j < cos0.length; j++) {
				if(j >= cos1.length)
					return 1;
				int key1 = cos0[j].compareTo(cos1[j]);
				if(key1 != 0)
					return key1;
			}
		}
		return 0;
	}
}

