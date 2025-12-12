package com.neocoretechs.rosai.lsh;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.neocoretechs.rosai.FloatTensor;
import com.neocoretechs.rosai.Parallel;

/**
 * An {@link Index} contains one or more locality sensitive hash tables. These hash
 * tables contain the mapping between a combination of a number of hashes
 * (encoded using an integer) and a list of possible nearest neighbors.
 *
 * A hash function can hash a vector of arbitrary dimensions to an integer
 * representation. The hash function needs to be locality sensitive to work in
 * the locality sensitive hash scheme, meaning that vectors that are 'close'
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
 * A good choice of w can significantly impact the performance of the LSH algorithm.
 * 
 */
final class HashTable implements Serializable {
	private static final Log log = LogFactory.getLog(HashTable.class);
	private static final long serialVersionUID = -5410017645908038641L;
	private static boolean DEBUG = true;
	/**
	 * Contains the mapping between a combination of a number of hashes (encoded
	 * using an integer) and a list of possible nearest neighbours
	 */
	private HashMap<Integer,List<FloatTensor>> hashTable;
	private CosineHash[] hashFunctions;
	private CosineHash family;
	private int index;

	/**
	 * Initialize a new hash table, it needs the number of hash
	 * functions that should be used and projection vector size. Its organized by index up to number of hash tables.
	 * @param index The projection vector and cosine hash number index.
	 * @param numberOfHashes The number of hash functions that should be used.
	 * @param projectionVectorSize The number of elements in the random projection vector
	 */
	public HashTable(int index, int numberOfHashes, int projectionVectorSize) {
		this.index = index;
		this.hashTable = new HashMap<Integer, List<FloatTensor>>();
		this.hashFunctions = new CosineHash[numberOfHashes];
		if(numberOfHashes > 64)
			Parallel.parallelFor(0, numberOfHashes, i -> {
				hashFunctions[i] = new CosineHash(projectionVectorSize);
			});
		else
			for(int i = 0; i < numberOfHashes; i++)
				hashFunctions[i] = new CosineHash(projectionVectorSize);
	}

	/**
	 * Query the hash table for a vector. It calculates the hash for the vector,
	 * and does a lookup in the hash table. If no candidates are found, an empty
	 * list is returned, otherwise, the list of candidates is returned.
	 * 
	 * @param query The query vector.
	 * @return Does a lookup in the tables for a query using its combined hash based on passed tensor. If no
	 *         candidates are found, an empty list is returned, otherwise, the
	 *         list of candidates is returned.
	 */
	public List<FloatTensor> query(FloatTensor query) {
		Integer combinedHash = hash(query);
		if(DEBUG)
			log.info("Combined hash for query:"+combinedHash);
		if(hashTable.containsKey(combinedHash))
			return hashTable.get(combinedHash);
		else
			return new ArrayList<FloatTensor>();
	}

	/**
	 * Add the vector to the map of hash tables based on key of combined hash generated from passed vector
	 * @param vector
	 */
	public void add(FloatTensor vector) {
		Integer combinedHash = hash(vector);
		if(!hashTable.containsKey(combinedHash)){
			hashTable.put(combinedHash, new ArrayList<FloatTensor>());
		}
		hashTable.get(combinedHash).add(vector);
	}

	/**
	 * Calculate the combined hash for a vector.
	 * @param vector The vector to calculate the combined hash for.
	 * @return An integer representing a combined hash.
	 */
	private Integer hash(FloatTensor vector){
		int hashes[] = new int[hashFunctions.length];
		for(int i = 0 ; i < hashFunctions.length ; i++){
			hashes[i] = hashFunctions[i].hash(vector);
		}
		Integer combinedHash = CosineHash.combine(hashes);
		return combinedHash;
	}

	/**
	 * Return the number of hash functions used in the hash table.
	 * @return The number of hash functions used in the hash table.
	 */
	public int getNumberOfHashes() {
		return hashFunctions.length;
	}

	@Override
	public String toString() {
		return String.format("%s index=%d family=%s hashes=%s tableSize=%d",this.getClass().getName(), index, family, Arrays.toString(hashFunctions), hashTable.size());
	}
}


