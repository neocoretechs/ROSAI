package com.neocoretechs.rosai.relatrix;

import java.io.IOException;
import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.neocoretechs.relatrix.DuplicateKeyException;
import com.neocoretechs.relatrix.Relation;
import com.neocoretechs.relatrix.Result;
import com.neocoretechs.relatrix.client.asynch.AsynchRelatrixClientTransaction;
import com.neocoretechs.relatrix.key.NoIndex;
import com.neocoretechs.rocksack.TransactionId;
import com.neocoretechs.rosai.lsh.CosineHash;

import com.neocoretechs.rosai.*;
import com.neocoretechs.rosai.ChatFormat.Message;
import com.neocoretechs.rosai.ChatFormat.Role;
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

	/**
	 * Contains the mapping between a combination of a number of hashes (encoded
	 * using an integer) and a list of possible nearest neighbors
	 */
	private List<CosineHash[]> hashTable;
	private UUID key;

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
		this.maxTokens = maxTokens;
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
			if(DEBUG)
				log.info("Querying combined hash for query "+i+" of "+hashTable.size()+":"+combinedHash);
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
	 * @param ts the timestamp
	 * @param initiator the initiator of the interaction; either USER or SYSTEM
	 * @param invocation
	 * @param response response
	 */
	public void addInteraction(ChatFormat chatFormat, Long ts, ChatFormat.Role initiator, ChatFormat.Message invocation, ChatFormat.Message response) {
		TimestampRole tr_assistant = new TimestampRole(ts, ChatFormat.Role.ASSISTANT);
		TimestampRole tr_user = new TimestampRole(ts, initiator);
		//try(Timer _ = Timer.log("addInteraction: SaveState of reponse:"+responseTokens.size()+" initiator:"+tr_user.toString())) {
			try {
				add(chatFormat, tr_user, invocation);
				add(chatFormat, tr_assistant, response);
			} catch (IllegalAccessException | ClassNotFoundException | IOException | InterruptedException | ExecutionException e) {
				log.error(e);
				dbClient.rollback(xid);
				return;
			}
			dbClient.commit(xid); // Only after both store ops succeed
		//}
	}	
	/**
	 * Add a vector to the index. Create a UUID and store the vector in a K/V datastore, use the UUID to
	 * reference the vector in the Relatrix relationship.
	 * @param chatFormat 
	 * @param timestampRole The map of the morphism to store LSH->TimestampRole->NoIndex key contains timestamp and role
	 * @param invocation the list of tokens
	 * @throws DuplicateKeyException attempt to insert duplicate combined hash.timestampRole key
	 * @throws IOException asynchronous database client exception
	 * @throws ClassNotFoundException asynchronous database client exception
	 * @throws IllegalAccessException asynchronous database client exception
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception
	 */
	public void add(ChatFormat chatFormat, TimestampRole timestampRole, ChatFormat.Message invocation) throws IllegalAccessException, ClassNotFoundException, IOException, InterruptedException, ExecutionException {
		FloatTensor fvec = normalize(chatFormat.stripFormatting(chatFormat.encodeAsList(invocation.content())));
		NoIndex noIndex = NoIndex.create(invocation);
		for(int i = 0; i < hashTable.size(); i++) {
			Integer combinedHash = hash(hashTable.get(i), fvec);
			CompletableFuture<Relation> res = dbClient.store(xid, combinedHash, timestampRole, noIndex);
			res.get();
		}
	}
	/**
	 * Find the nearest candidates using cosine similarity. If none are found get the lset timestsamp
	 * retrieve that vector, then get the other vectors with the LSH index and obtain
	 * the most relevant.
	 * @param chatFormat chatFormat instance
	 * @param promptFrame list of messages to populate starting with initial request
	 * @return List of retrieved messages
	 * @throws ExecutionException asychronous database client exception
	 * @throws InterruptedException asychronous database client exception
	 * @throws IOException asychronous database client exception
	 * @throws IllegalAccessException asychronous database client exception
	 * @throws ClassNotFoundException asychronous database client exception
	 * @throws IllegalArgumentException asychronous database client exception
	 */
	public List<ChatFormat.Message> findNearest(ChatFormat chatFormat, ChatFormat.Message promptFrame) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		List<Result> nearest = null;
		//List<Integer> results = (List<Integer>)promptFrame.encode();
		//transition to String vector from tokenized Groff 2/4/26
		List<Integer> results = chatFormat.stripFormatting(chatFormat.encodeAsList(promptFrame.content()));
		if(DEBUG)
			log.info("findNearest User query has "+results.size()+" tokens from "+promptFrame);
		List<ChatFormat.Message> returns = new ArrayList<ChatFormat.Message>();
		FloatTensor fmessage = normalize(results);
		nearest = queryParallel(fmessage);
		if(DEBUG)
			log.info("findNearest Retrieved "+nearest.size()+" entries from LSH index query.");
		// If we retrieved nothing from semantic query of initial message, try getting last timestamp
		if(nearest.isEmpty()) {
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
				nearest = queryParallel(lshQuery);
			}
			// we could have come up index and timestamp empty
			if(nearest == null || nearest.isEmpty()) {
				// put most recent user query last
				returns.add(promptFrame);
				if(DEBUG)
					log.info("findNearest early Returning from empty index and timestamp query with original prompt");
				return returns;
			}
		}
		// nearest has Result(s) from the last series of TimestampRole query, TimestampRole LSH index, and/or original message
		// fmessage is our original message, mormalized as FloatTensor
		// organize our current Results and find similar relevant entries via cosine similarity
		// and theta similarity
		// organize their indexes in a TreeMap in descending order of cosDist, index in Result
		double[] thetasim = new double[nearest.size()];
		@SuppressWarnings("unused")
		int cnt = 0;
		TreeMap<Double, Integer> tm = new TreeMap<Double, Integer>();
		for(int i = 0; i < nearest.size(); i++) {
			Result result = nearest.get(i);
			// Original LSH at Result.get(0), TimestampRole at Result.get(1), message at Result.get(2)
			NoIndex noIndex = (NoIndex) result.get(2);
			//List<Integer> restensor = (List<Integer>)noIndex.getInstance();
			List<Integer> restensor = chatFormat.stripFormatting(chatFormat.encodeAsList(((ChatFormat.Message)noIndex.getInstance()).content()));
			double cosDist;
			FloatTensor cantensor = normalize(restensor);
			if(cantensor.size() < fmessage.size())
				cosDist = fmessage.dot(0, cantensor, 0, cantensor.size());
			else
				cosDist = cantensor.dot(0, fmessage, 0, fmessage.size());
			cosDist = Math.acos(cosDist); // radians
			thetasim[i] = cosDist;
			if(DEBUG)
				log.info("findNearest retrieved result set dialog index:"+i+" of "+nearest.size()+" theta="+cosDist+" .) "+result.get(0));//+" "+DeviceManager.decode(restensor));
			tm.put(cosDist, i);
		}
		// descending cos similarity order
		NavigableMap<Double, Integer> nm = tm.descendingMap();
		// flatten to list of descending cos order index into Result set of query TimestampRole->Message
		List<Integer> valueList = nm.values().stream().collect(Collectors.toList());
		// list of eventual insertion points into valueList
		ArrayList<Integer> insertList = new ArrayList<Integer>();
		ArrayList<Boolean> insertAfter = new ArrayList<Boolean>();
		// walk over interactions
		int listCtr = 0;
		while(listCtr < valueList.size()) {
			TimestampRole tsRole = (TimestampRole) ((Result)nearest.get(valueList.get(listCtr))).get(1);
			switch(tsRole.getRole()) {
			case Role.SYSTEM:
			case Role.USER:
				if(listCtr+1 >= valueList.size() || !(((TimestampRole)((Result)nearest.get(valueList.get(listCtr+1))).get(1))).getRole().equals(Role.ASSISTANT)) {
					insertList.add(listCtr);
					insertAfter.add(true); //entry is USER, next is NOT ASSISTANT
					if(DEBUG)
						log.info("findNearest role USER insert after "+listCtr);
					// now advance to next entry
					++listCtr;	
				} else
					// advance list by 2, since 'next' entry is valid response of 'ASSISTANT'
					listCtr+=2;
				break;
			case Role.ASSISTANT:
				// role is assistant without previous USER or SYSTEM, otherwise we would have skipped this entry
				insertList.add(listCtr);
				insertAfter.add(false); // insert before because entry is ASSISTANT
				if(DEBUG)
					log.info("findNearest role ASSISTANT insert before "+listCtr);
				++listCtr;
				break;
			default:
				valueList.remove(listCtr);
				log.error("Unknown role encountered");
				break;
			}
		}
		if(DEBUG)
			log.info("findNearest insert list size:"+insertList.size());
		// Now we have our insertList and our BeforeAfter list, process those performing another parallel query
		// to try and resolve associated interaction elements that are missing and then insert those into our
		// nearest list if found, if we cant locate a corresponding interaction element, then remove the entry
		// we will create a TimestanpRole with a matching timestamp and the opposite role, the launch the query.
		List<Object> timestampRoleQuery = new ArrayList<Object>();
		for(Integer i : insertList) {
			Result res = nearest.get(i);
			TimestampRole tsr = new TimestampRole();
			tsr.setTimestamp(((TimestampRole)res.get(1)).getTimestamp());
			if(((TimestampRole)res.get(1)).getRole().equals(Role.ASSISTANT))
				tsr.setRole(Role.USER);
			else
				tsr.setRole(Role.ASSISTANT);
			if(DEBUG)
				log.info("findNearest setting timestampRoleQuery "+tsr+" for nearest "+res);
			timestampRoleQuery.add(tsr);
		}
		if(DEBUG)
			log.info("findNearest timestampRoleQuery size:"+timestampRoleQuery.size());
		if(!timestampRoleQuery.isEmpty()) {
			List<Result> timestampRoleResult = queryParallelMap(timestampRoleQuery);
			// now we have a set of new Results with missing elements retrieved, insert those into our
			// nearest table as 2 element Result sets interposed with our original 3 element result
			// sets that the LSH index, we will have to differentiate as we extract the final messages
			if(DEBUG)
				log.info("findNearest parallel query timestampRoleResult size:"+timestampRoleResult.size());
			if(!timestampRoleResult.isEmpty()) {
				// iterate through insertList and match the timestamp with our results
				// then if found, perform the insert and modify the insertList after each insert
				for(int insertListPos = 0; insertListPos < insertList.size(); insertListPos++) {
					int nearestPos = insertList.get(insertListPos);
					boolean nearestInsertAfter = insertAfter.get(insertListPos);
					Result nearestResult = (Result)nearest.get(nearestPos);
					if(DEBUG)
						log.info("findNearest nearestPos="+nearestPos+" nearestInsertAfter="+nearestInsertAfter+" nearestResult="+nearestResult);
					TimestampRole ts = (TimestampRole) nearestResult.get(1);
					// find the timestamp in the query result
					boolean timestampMatch = false;
					for(Result queryResult: timestampRoleResult) {
						TimestampRole timestampQueryResult = (TimestampRole) queryResult.get(0);
						if(DEBUG)
							log.info("findNearest timestampQueryResult="+timestampQueryResult+" nearestResult ts="+ts);
						if(timestampQueryResult.getTimestamp() == ts.getTimestamp()) {
							// now we have insertList at insertListPos, and timestampQueryResult match so insert queryResult
							// at insertListPos in nearest based on insertList and beforeAfter
							if(!nearestInsertAfter) {
								// insert before, normal insert, add one to nearestPos element and subsequent indices
								nearest.add(nearestPos, queryResult);
								if(DEBUG)
									log.info("findNearest insert before to nearest at "+nearestPos+" queryResult:"+queryResult);
								// update the insert positions in nearest
								for(int insertListCtr = 0; insertListCtr < insertList.size(); insertListCtr++) {
									if(insertList.get(insertListCtr) >= nearestPos) {
										// we inserted before, so all elements >= insert position have to be incremented
										insertList.set(insertListCtr,insertList.get(insertListCtr)+1);
										if(DEBUG)
											log.info("findNearest insert before updating insertList at:"+insertListCtr+" to "+insertList.get(insertListCtr));
									}	
								}
							} else {
								// insert after, in other words before next specified element
								nearest.add(nearestPos+1, queryResult);
								if(DEBUG)
									log.info("findNearest insert after to nearest at "+nearestPos+" queryResult:"+queryResult);
								// update the insert positions in nearest
								for(int insertListCtr = 0; insertListCtr < insertList.size(); insertListCtr++) {
									if(insertList.get(insertListCtr) > nearestPos) {
										// we inserted after, so all elements > insert position have to be incremented
										insertList.set(insertListCtr,insertList.get(insertListCtr)+1);
										if(DEBUG)
											log.info("findNearest insert after updating insertList at:"+insertListCtr+" to "+insertList.get(insertListCtr));
									}
								}
							}
							timestampMatch = true;
						}
					}
					if(!timestampMatch)
						log.info("findNearest >>> No matching timestamp for nearestResult:"+nearestResult);
				}
			}
		}
		// calculate how much of nearest we can insert
		int nearestEntries = 0;
		int contentSize = 0;
		for(; nearestEntries < nearest.size(); nearestEntries++) {
			Result result = ((Result)nearest.get(nearestEntries));
			ChatFormat.Message message = (ChatFormat.Message)((NoIndex)result.get(result.length()-1)).getInstance();
			contentSize += message.content().length();
			if(contentSize >= (maxTokens - (((float)maxTokens) * .3)))
				break;
		}
		if(nearestEntries+1 <= nearest.size())
			nearestEntries = Math.round(((float)nearestEntries) / 2) * 2;
		if(DEBUG)
			log.info("findNearest "+tm.values().size()+" original context entries, current nearest="+nearest.size()+" content size="+contentSize+" max:"+(maxTokens - (((float)maxTokens) * .3))+" max possible nearest entries="+nearestEntries);
		for(int i = 0; i < nearestEntries; i++ ) {
			Result result = nearest.get(i);
			returns.add((ChatFormat.Message)((NoIndex)result.get(result.length()-1)).getInstance());
		}
		// put most recent user query last
		returns.add(promptFrame);
		if(DEBUG) {
			for(int i = 0; i < returns.size(); i++) {
				log.info(i+".) "+returns.get(i));
			}
		}
		return returns;
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
		if(DEBUG)
			log.info("getTimestampRole for "+trr);
		//CompletableFuture<Stream> cit = dbClient.findStream(xid, '*', trr, '?');
		//cit.get().forEach(e->{
		CompletableFuture<Iterator> cit = dbClient.findSet(xid, '*', trr, '?');
		Iterator<?> it = cit.get();
		// get one instance
		if(it.hasNext()) {
			if(DEBUG)
				log.info("getTimeStampRole result");
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
					// should be LSH index, NoIndex Message
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

