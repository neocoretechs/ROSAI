package com.neocoretechs.rosai.lsh;

import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ThreadLocalRandom;

import com.neocoretechs.rosai.F32FloatTensor;
import com.neocoretechs.rosai.FloatTensor;
import com.neocoretechs.rosai.Parallel;

/**
 * <h3>LSH (Locality-Sensitive Hashing) is a technique used for efficient similarity search and clustering of high-dimensional data. 
 * Cosine hashing is one type of LSH.</h3>
 * In cosine hashing, the goal is to map similar vectors (in terms of cosine similarity) to the same or nearby hash buckets 
 * with high probability. The hash function is designed such that the probability of two vectors being mapped to the same 
 * bucket is proportional to their cosine similarity.<p>
 * Given two vectors x and y, the cosine similarity is defined as:<br>
 * cos(x, y) = (x · y) / (||x|| ||y||) <br>
 * where x · y is the dot product of x and y, and ||x|| and ||y|| are the magnitudes (norms) of x and y, respectively. <p>
 * In cosine hashing, we use a random projection vector w to compute the hash value. Specifically, the hash function is defined as:<br>
 * h(x) = sign(w · x) <br>
 * where sign is a function that returns 1 if the dot product is positive and 0 otherwise. <p>
 * Relation of distance to number of hashes: <br>
 * The key idea behind LSH is that if two vectors are similar (i.e., have high cosine similarity), 
 * they are more likely to be mapped to the same bucket. The probability of two vectors being mapped to the same bucket is given by: <br>
 * P(h(x) = h(y)) = 1 - (θ(x, y) / π) <br>
 * where θ(x, y) is the angle between x and y.<p>
 * To increase the accuracy of the similarity search, we use multiple hash functions (i.e., multiple random projection vectors w).<p>
 * The number of hashes required to achieve a certain level of accuracy depends on the desired similarity threshold and the 
 * dimensionality of the data. <p>
 * In general, the more hashes we use, the more accurate the similarity search will be. However, using too many hashes can lead to 
 * increased computational cost and storage requirements.<p>
 * Trade-off:<br>
 * There is a trade-off between the number of hashes and the accuracy of the similarity search.<p> 
 * Increasing the number of hashes improves the accuracy but also increases the computational cost and storage requirements.<br>
 * In practice, the number of hashes is typically chosen based on the specific requirements of the application, such as the 
 * desired level of accuracy, the size of the dataset, and the available computational resources.<p>
 * By using multiple hash functions and combining the results, LSH can efficiently identify similar vectors in high-dimensional space, 
 * making it a powerful technique for similarity search and clustering applications.
 *
 */
public final class CosineHash implements Serializable, Comparable {
	private static final long serialVersionUID = 778951747630668248L;
	FloatTensor randomProjection;

	public CosineHash() {}

	public CosineHash(int dimensions){
		ThreadLocalRandom rand  = ThreadLocalRandom.current();
		float[] randomp = new float[dimensions];
		if(dimensions > 1000) {
			Parallel.parallelFor(0, dimensions, d -> {
				double val = rand.nextGaussian();
				//randomProjection.setFloat(d, (float) val);
				randomp[d] = (float)val;
			});
		} else {
			for(int d=0; d < dimensions; d++) {
				double val = rand.nextGaussian();
				//randomProjection.setFloat(d, (float) val);
				randomp[d] = (float)val;
			}
		}
		MemorySegment segment = MemorySegment.ofArray(randomp);
		randomProjection = new F32FloatTensor(dimensions, segment);
	}

	public static Integer combine(int[] hashes) {
		//Treat the hashes as a series of bits.
		//They are either zero or one, the index 
		//represents the value.
		int result = 0;
		//factor holds the power of two.
		int factor = 1;
		for(int i = 0 ; i < hashes.length ; i++){
			result += hashes[i] == 0 ? 0 : factor;
			factor *= 2;
		}
		return result;
	}

	public int hash(FloatTensor vector) {
		//calculate the dot product.
		double result;
		if(vector.size() < randomProjection.size())
			result = randomProjection.dot(0, vector, 0, vector.size());
		else
			result = vector.dot(0, randomProjection, 0, randomProjection.size());
		//returns a 'bit' encoded as an integer.
		//1 when positive or zero, 0 otherwise.
		return result > 0 ? 1 : 0;
	}

	@Override
	public String toString(){
		//return String.format("%s\nrandomProjection:%s",this.getClass().getName(), randomProjection);
		return String.format("%s randomProjectionSize=%d",this.getClass().getName(), randomProjection.size());
	}

	@Override
	public int compareTo(Object arg0) {
		return randomProjection.compareTo(arg0);
	}
}
