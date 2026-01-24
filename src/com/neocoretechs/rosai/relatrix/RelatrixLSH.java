package com.neocoretechs.rosai.relatrix;

import java.io.IOException;
import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
	 *         list of candidates is returned as List<Result> where Result contains timetamp, NoIndex with vector
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
	 * Query the hash table in parallel for a vector of tokens. It calculates the hash for the vector,
	 * and does a lookup in the hash table. If no candidates are found, an empty
	 * list is returned, otherwise, the list of candidates is returned.
	 * 
	 * @param query The query vector.
	 * @param normalizedQuery the FloatTensor of normalization applied to tokenized query
	 * @return Does a lookup in the table for a query using its hash. If no
	 *         candidates are found, an empty list is returned, otherwise, the
	 *         list of candidates is returned as List<Result> where Result contains TimestampRole, vector
	 * @throws IOException asynchronous database client exception
	 * @throws IllegalAccessException asynchronous database client exception
	 * @throws ClassNotFoundException asynchronous database client exception
	 * @throws IllegalArgumentException asynchronous database client exception 
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception 
	 */
	public List<Result> queryParallel(List<Integer> query, FloatTensor normalizedQuery) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		List<Result> res = new ArrayList<Result>();
		ArrayList<Object> iq = new ArrayList<Object>();
		for(int i = 0; i < hashTable.size(); i++) {
			Integer combinedHash = hash(hashTable.get(i), normalizedQuery);
			iq.add(combinedHash);
		}
		//try (var _ = Timer.log("Querying combined hash for table of "+hashTable.size())) {
			CompletableFuture<List> cres = dbClient.findSetParallel(xid, iq, '?', '?');
			res = cres.get();
			//for(Result r: res) {
			// should be NoIndex values
			//}
		//}
		return res;
	}
	/**
	 * Query the hash table in parallel for a vector of LSH indexes. The query has the indexes,
	 * and uses these directly to do a lookup. If no candidates are found, an empty
	 * list is returned, otherwise, the list of candidates is returned.
	 * 
	 * @param query The query vector.
	 * @return Does a lookup in the table for a query using its hash. If no
	 *         candidates are found, an empty list is returned, otherwise, the
	 *         list of candidates is returned as List<Result> where Result contains TimestampRole, vector
	 * @throws IOException asynchronous database client exception
	 * @throws IllegalAccessException asynchronous database client exception
	 * @throws ClassNotFoundException asynchronous database client exception
	 * @throws IllegalArgumentException asynchronous database client exception
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception
	 */
	public List<Result> queryParallel2(List<Object> query) throws IllegalArgumentException, ClassNotFoundException, IllegalAccessException, IOException, InterruptedException, ExecutionException {
		List<Result> res = null;
		//try (var _ = Timer.log("Querying combined hash for List of "+query.size())) {
			CompletableFuture<List> cres = dbClient.findSetParallel(xid, query, '?', '?');
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
	 * @param ts the timestamp
	 * @param initiator the initiator of the interaction; either USER or SYSTEM
	 * @param userMessage tokenized initiator message
	 * @param responseTokens tokenized response
	 */
	public void addInteraction(Long ts, ChatFormat.Role initiator, List<Integer> userMessage, List<Integer> responseTokens) {
		TimestampRole tr_assistant = new TimestampRole(ts, ChatFormat.Role.ASSISTANT);
		TimestampRole tr_user = new TimestampRole(ts, initiator);
		//try(Timer _ = Timer.log("addInteraction: SaveState of reponse:"+responseTokens.size()+" initiator:"+tr_user.toString())) {
			try {
				add(tr_user, userMessage);
				add(tr_assistant, responseTokens);
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
	 * @param timestampRole The map of the morphism to store LSH->TimestampRole->NoIndex key contains timestamp and role
	 * @param vector the list of tokens
	 * @throws DuplicateKeyException attempt to insert duplicate combined hash.timestampRole key
	 * @throws IOException asynchronous database client exception
	 * @throws ClassNotFoundException asynchronous database client exception
	 * @throws IllegalAccessException asynchronous database client exception
	 * @throws ExecutionException asynchronous database client exception
	 * @throws InterruptedException asynchronous database client exception
	 */
	public void add(TimestampRole timestampRole, List<Integer> vector) throws IllegalAccessException, ClassNotFoundException, IOException, InterruptedException, ExecutionException {
		FloatTensor fvec = normalize(vector);
		NoIndex noIndex = NoIndex.create(vector);
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
		List<Integer> results = (List<Integer>)promptFrame.encode();
		if(DEBUG)
			log.info("findNearest User query has "+results.size()+" tokens from "+promptFrame);
		List<ChatFormat.Message> returns = new ArrayList<ChatFormat.Message>();

		FloatTensor fmessage = normalize(results);
		nearest = queryParallel(results, fmessage);
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
					lshQuery.add(result.get(0));
				}
				// now query the matching LSH indexes we got from each timestamp
				nearest = queryParallel2(lshQuery);
			}
			// we could have come up index and timestamp empty
			if(nearest == null || nearest.isEmpty()) {
				// put most recent user query last
				returns.add(promptFrame);
				if(DEBUG)
					log.info("findNearest Returning from empty index and timestamp query with original prompt");
				return returns;
			}
		}
		// nearest has Result(s) from the last series of TimestampRole query, TimestampRole LSH index, and/or original message
		// fmessage is our original message, mormalized as FloatTensor
		// organize our current Results and find similar relevant entries via cosine similarity
		// and theta similarity
		// organize their indexes in a TreeMap in descending order of cosDist, index in Result
		double[] thetasim = new double[nearest.size()];
		int cnt = 0;
		TreeMap<Double, Integer> tm = new TreeMap<Double, Integer>();
		for(int i = 0; i < nearest.size(); i++) {
			Result result = nearest.get(i);
			// TimestampRole at Result.get(0)
			NoIndex noIndex = (NoIndex) result.get(1);
			List<Integer> restensor = (List<Integer>)noIndex.getInstance();
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
		// Now we need another TreeMap with the entries in TimestampRole descending order. As we
		// walk the cosine tree we check the timestamp tree for proper order of time/role
		// and if we dont see the order we superimpose it for that entry in the returns
		TreeMap<TimestampRole, Integer> tr = new TreeMap<TimestampRole, Integer>();
		for(int i = 0; i < nearest.size(); i++) {
			Result result = nearest.get(i);
			TimestampRole trr = (TimestampRole) result.get(0);
			tr.put(trr, i);
		}
		// tr is sorted by timestamp ASSISTANT, timestamp SYSTEM, timestamp USER timestamp ascending
		// Walk the TreeMap in ascending theta similarity order, fill our
		// context tokens until we get to maximum context length -30% for response overhead.
		// At each entry, check the role and retrieve the proper counterpart; If assistant, 
		// get user with same timestamp if it wasnt the previous entry. if user, get assistant
		// with same timestamp if its not the next entry. In essence we want question/answer pairs
		// that are relevant. If we are missing an answer, get it, if we are missing a question, get that
		// if one of the pairs was picked for semantic relevance and the other wasnt.
		// We have the entries retrieved via sematic relevance or recent timestamp in these TreeMaps.
		// One we walk in order of theta ascending, the other we have sorted by TimestampRole that we
		// check if we see an out of order pair, and if that map doesnt have the missing pair member we need
		// with our sorted check; TimestampRole key being either higherEntry or lowerEntry, we go out
		// to the database and get it by constructing a TimestampRole with same timestamp, complimentary role,
		// insert it to results, and up our context token count toward max.
		Iterator<Integer> it = tm.values().iterator();
		boolean wasUser = false; // we need a user first, our user query that initiated this interaction goes in last
		while(it.hasNext() && results.size() < (maxTokens - (((float)maxTokens) * .3))) {
			int i = it.next();
			Result result = nearest.get(i);
			TimestampRole trr =  (TimestampRole) result.get(0);
			if(trr.getRole().equals(ChatFormat.Role.ASSISTANT)) {
				if(!wasUser) {
					Map.Entry<TimestampRole, Integer> trn = tr.higherEntry(trr); // system or user same timestamp?
					if(trn != null && (trn.getKey().getTimestamp() == trr.getTimestamp())) { 		
						if(trn.getKey().getRole() == ChatFormat.Role.USER) {
							Result result2 = nearest.get(trn.getValue()); // index to nearest list
							addRetrievedMessage(result2, results, chatFormat, returns);
						} else {
							if(trn.getKey().getRole() == ChatFormat.Role.SYSTEM) {
								trn = tr.higherEntry(tr.higherKey(trr));
								if(trn != null && trn.getKey().getRole() == ChatFormat.Role.USER) {
									Result result2 = nearest.get(trn.getValue()); // index to nearest list
									addRetrievedMessage(result2, results, chatFormat, returns);
								} else {
									TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.USER);
									getTimestampRole(results, chatFormat, returns, tr2);
								}
							} else {
								TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.USER);
								getTimestampRole(results, chatFormat, returns, tr2);
							}
						}
					} else {
						TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.USER);
						getTimestampRole(results, chatFormat, returns, tr2);
					}
				} else {
					wasUser = false; // fall through
				}
			} else {
				if(trr.getRole().equals(ChatFormat.Role.USER)) {
					if(wasUser) { // 2 users
						Map.Entry<TimestampRole, Integer> trn = tr.lowerEntry(trr); // system or assistant same timestamp?
						if(trn != null && (trn.getKey().getTimestamp() == trr.getTimestamp())) {
							if(trn.getKey().getRole() == ChatFormat.Role.ASSISTANT) {
								Result result2 = nearest.get(trn.getValue()); // index to nearest list
								addRetrievedMessage(result2, results, chatFormat, returns);
							} else {
								if(trn.getKey().getRole() == ChatFormat.Role.SYSTEM) {
									trn = tr.lowerEntry(tr.lowerKey(trr)); 
									// lower key of user is assistant same timestamp?
									if(trn != null && trn.getKey().getRole() == ChatFormat.Role.ASSISTANT) {
										Result result2 = nearest.get(trn.getValue()); // index to nearest list
										addRetrievedMessage(result2, results, chatFormat, returns);
									} else {
										TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.ASSISTANT);
										getTimestampRole(results, chatFormat, returns, tr2);
									}
								} else {
									TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.ASSISTANT);
									getTimestampRole(results, chatFormat, returns, tr2);
								}
							}					
						} else {
							TimestampRole tr2 = new TimestampRole(trr.getTimestamp(), ChatFormat.Role.ASSISTANT);
							getTimestampRole(results, chatFormat, returns, tr2);
						}
					} else {
						wasUser = true;
					}
				}
			}
			addRetrievedMessage(result, results, chatFormat, returns);
			++cnt;
		}
		if(DEBUG)
			log.info("findNearest "+cnt+" results inserted into context. Similarities:"+Arrays.toString(thetasim));
		// if no interactions played out, lets build a system level series of responses with what we have
		if(cnt == 0) {
			if(DEBUG)
				log.info("findNearest "+tm.values().size()+" context entries, current results:"+results.size()+" max:"+(maxTokens - (((float)maxTokens) * .3)));
			it = tm.values().iterator();
			while(it.hasNext() && results.size() < (maxTokens - (((float)maxTokens) * .3))) {
				int i = it.next();
				Result result = nearest.get(i);
				TimestampRole trr =  (TimestampRole) result.get(0);
				trr.setRole(ChatFormat.Role.SYSTEM);
				addRetrievedMessage(result, trr, results, chatFormat, returns);
			}
		}
		// put most recent user query last
		returns.add(promptFrame);
		return returns;
	}
	/**
	 * Add the retrieved Result of 0 - TimestampRole 1 - NoIndex vector of List<Integer> to returns list
	 * as ChatFormat.Message
	 * @param result Result result set from retrieval
	 * @param results tokenized results
	 * @param chatFormat chatFormat instance
	 * @param returns List of ChatFormat.Messge returns
	 */
	private void addRetrievedMessage(Result result, List<Integer> results, ChatFormat chatFormat, List<ChatFormat.Message> returns) {
		NoIndex noIndex = (NoIndex) result.get(1);
		List<Integer> restensor = (List<Integer>)noIndex.getInstance();
		results.addAll(restensor); // to keep track of max context size
		String resString = DeviceManager.decode(chatFormat, restensor);
		if(DEBUG)
			log.info("addRetrievedMessage:"+(TimestampRole)result.get(0)+" for NoIndex tokens="+results.size()+" decode="+resString.length());
		// calculate running total
		int totalLen = 0;
		for(ChatFormat.Message messg : returns)
			totalLen += messg.content().length();
		if(totalLen+resString.length() >= maxTokens) {
			log.info("addRetrievedMessage: addition of retrieved result of length "+resString+" would exceed context length by "+(totalLen+resString.length()-maxTokens));
			return;
		}
		returns.add(new ChatFormat.Message(chatFormat, ((TimestampRole)result.get(0)).getRole(), resString));
	}
	/**
	 * Permutation for existing TimestampRole, Result 0 - NoIndex vector
	 * add List<Integer> to returns list as ChatFormat.Message
	 * @param result result set from retrieval
	 * @param trr TimestampRole retrieved TimestampRole
	 * @param results tokenized results
	 * @param chatFormat chatFormat instance
	 * @param returns List of ChatFormat.Message returns
	 */
	private void addRetrievedMessage(Result result, TimestampRole trr, List<Integer> results, ChatFormat chatFormat, List<ChatFormat.Message> returns) {
		NoIndex noIndex = (NoIndex) result.get(0);
		List<Integer> restensor = (List<Integer>)noIndex.getInstance();
		results.addAll(restensor); // to keep track of max context size
		String resString = DeviceManager.decode(chatFormat, restensor);
		if(DEBUG)
			log.info("addRetrievedMessage: existing TimestampRole:"+trr+" decode="+resString.length());
		// calculate running total
		int totalLen = 0;
		for(ChatFormat.Message messg : returns)
			totalLen += messg.content().length();
		if(totalLen+resString.length() >= maxTokens) {
			log.info("addRetrievedMessage: addition of retrieved result of length "+resString+" would exceed context length by "+(totalLen+resString.length()-maxTokens));
			return;
		}
		returns.add(new ChatFormat.Message(chatFormat, trr.getRole(), resString));
	}
	/**
	 * Get the NoIndex vector of List<Integer> for existing TimestampRole
	 * @param results List of tokenized results
	 * @param chatFormat chatFormat instance
	 * @param returns running list of ChatFormat.message retrievals
	 * @param trr target TimestampRole
	 * @throws InterruptedException asynchronous db client exception
	 * @throws ExecutionException asynchronous client/database exception
	 */
	private void getTimestampRole(List<Integer> results, ChatFormat chatFormat, List<ChatFormat.Message> returns, TimestampRole trr) throws InterruptedException, ExecutionException {
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
			addRetrievedMessage((Result)it.next(), trr, results, chatFormat, returns);
			//});
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
					// should be LSH index, NoIndex List<Integer> vector values
					res.add((Result) it.next());
				}
			//}
		}
		if(DEBUG)
			log.info("primeByTime returned "+res.size()+" results.");
		return res;
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

