/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.model;

/**
 * The {@link IndexHashTable} is a hash table which maps entries
 * of an array to their index in the array. All entries in the array must
 * be unique otherwise a well-defined mapping is not possible.
 * <p>
 * The entry objects must implement {@link Object#equals(Object)} and
 * {@link Object#hashCode()} otherwise the behavior of this class is
 * undefined.
 * <p>
 * The implementation uses a hash table with open addressing and linear probing.
 * <p>
 * The table is thread safe and can concurrently accessed by multiple threads,
 * thread safety is achieved through immutability. Though its not strictly immutable
 * which means, that the table must still be safely published to other threads.
 */
public class IndexHashTable<T> {

	private final Object keys[];
	private final int values[];
	
	private final int size;
	
	/**
	 * Initializes the current instance. The specified array is copied
	 * into the table and later changes to the array do not affect this
	 * table in any way. 
	 * 
	 * @param mapping the values to be indexed, all values must be unique otherwise
	 * a well-defined mapping of an entry to an index is not possible
	 * @param loadfactor the load factor, usually 0.7
	 * 
	 * @throws IllegalArgumentException if the entries are not unique
	 */
	public IndexHashTable(T mapping[], double loadfactor) {
		if (loadfactor <= 0 || loadfactor > 1)
			throw new IllegalArgumentException("loadfactor must be larger than 0 " +
					"and equal to or smaller than 1!");
		
		int arraySize = (int) (mapping.length / loadfactor) + 1;
		
		keys = new Object[arraySize];
		values = new int[arraySize];
		
		size = mapping.length;
		
		for (int i = 0; i < mapping.length; i++) {
			int startIndex = indexForHash(mapping[i].hashCode(), keys.length);
			
			int index = searchKey(startIndex, null, true);
			
			if (index == -1)
				throw new IllegalArgumentException("Array must contain only unique keys!");
			
			keys[index] = mapping[i];
			values[index] = i;
		}
	}
	
	private static int indexForHash(int h, int length) {
		return (h & 0x7fffffff) % length;
	}
	
	private int searchKey(int startIndex, Object key, boolean insert) {
		
		
		for (int index = startIndex; true; index = (index+1) % keys.length) {
			
			// The keys array contains at least one null element, which guarantees
			// termination of the loop
			if (keys[index] == null) {
				if (insert)
					return index;
				else
					return -1;
			}
			
			if (keys[index].equals(key)) {
				if (!insert)
					return index;
				else
					return -1;
			}
		}
	}
	
	/**
	 * Retrieves the index for the specified key.
	 * 
	 * @param key 
	 * @return the index or -1 if there is no entry to the keys
	 */
	public int get(T key) {
		
		int startIndex = indexForHash(key.hashCode(), keys.length);
			
		int index = searchKey(startIndex, key, false);
		
		if (index != -1) {
			return values[index];
		}
		else {
			return -1;
		}
	}
	
	/**
	 * Retrieves the size.
	 * 
	 * @return the number of elements in this map.
	 */
	public int size() {
		return size;
	}
	
	@SuppressWarnings("unchecked")
	public T[] toArray(T array[]) {
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] != null)
				array[values[i]] = (T) keys[i];
		}
		
		return array;
	}
}
