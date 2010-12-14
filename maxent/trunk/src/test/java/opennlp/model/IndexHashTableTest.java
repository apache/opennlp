package opennlp.model;

import junit.framework.TestCase;

public class IndexHashTableTest extends TestCase {

	public void testWithoutCollision() {
		
		String array[] = new String[3];
		
		array[0] = "4";
		array[1] = "7";
		array[2] = "5";
		
		IndexHashTable<String> arrayIndex = new IndexHashTable<String>(array, 1d);
		
		for (int i = 0; i < array.length; i++) 
			assertEquals(i, arrayIndex.get(array[i]));
	}
	
	public void testWitCollision() {
		
		String array[] = new String[3];
		
		array[0] = "7";
		array[1] = "21";
		array[2] = "0";
		
		IndexHashTable<String> arrayIndex = new IndexHashTable<String>(array, 1d);
		
		for (int i = 0; i < array.length; i++) 
			assertEquals(i, arrayIndex.get(array[i]));
		
		// has the same slot as as ""
		assertEquals(-1, arrayIndex.get("4"));
	}
}
