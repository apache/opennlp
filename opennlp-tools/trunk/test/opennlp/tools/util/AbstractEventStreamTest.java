///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2008 OpenNlp
// 
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
// 
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Lesser General Public License for more details.
// 
//You should have received a copy of the GNU Lesser General Public
//License along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////

package opennlp.tools.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;
import opennlp.model.Event;

/**
 * Tests for the {@link AbstractEventStream} class.
 */
public class AbstractEventStreamTest extends TestCase {

  enum RESULT {
    EVENTS,
    EMPTY
  };

  
  /**
   * This class extends the {@link AbstractEventStream} to help
   * testing the {@link AbstractEventStream#hasNext()}
   * and {@link AbstractEventStream#nextEvent()} methods.
   */
  class TestEventStream extends AbstractEventStream<RESULT> {

    
    public TestEventStream(Iterator<RESULT> samples) {
      super(samples);
    }
    
    /**
     * Creates {@link Iterator}s for testing.
     * 
     * @param sample parameter to specify the output
     * 
     * @return it returns an {@link Iterator} which contains one
     * {@link Event} object if the sample parameter equals
     * {@link RESULT#EVENTS} or an empty {@link Iterator} if the sample
     * parameter equals {@link RESULT#EMPTY}.
     */
    @Override
    protected Iterator<Event> createEvents(RESULT sample) {
      
      if (RESULT.EVENTS.equals(sample)) {
        List<Event> events = new ArrayList<Event>();
        events.add(new Event("test", new String[]{"f1", "f2"}));
        
        return events.iterator();
      }
      else if (RESULT.EMPTY.equals(sample)) {
        List<Event> emptyList = Collections.emptyList();
        return emptyList.iterator();
      }
      else {
        // throws runtime exception, execution stops here
        fail();
        
        return null;
      }
    }
    
  }
  
  /**
   * Checks if the {@link AbstractEventStream} behavior is correctly
   * if the {@link AbstractEventStream#createEvents(Object)} method
   * return iterators with events and empty iterators.
   */
  public void testStandardCase() {
    
    List<RESULT> samples = new ArrayList<RESULT>();
    samples.add(RESULT.EVENTS);
    samples.add(RESULT.EMPTY);
    samples.add(RESULT.EVENTS);
    
    TestEventStream eventStream = new TestEventStream(samples.iterator());
    
    int eventCounter = 0;
    while (eventStream.hasNext()) {
      eventStream.nextEvent();
      eventCounter++;
    }
    
    assertEquals(2, eventCounter);
  }
  
  /**
   * Checks if the {@link AbstractEventStream} behavior is correctly
   * if the {@link AbstractEventStream#createEvents(Object)} method
   * only returns empty iterators.
   */
  public void testEmtpyEventStream() {
    List<RESULT> samples = new ArrayList<RESULT>();
    samples.add(RESULT.EMPTY);
    
    TestEventStream eventStream = new TestEventStream(samples.iterator());
    assertEquals(false, eventStream.hasNext());
    
    // now check if it can handle multiple empty event iterators
    samples.add(RESULT.EMPTY);
    samples.add(RESULT.EMPTY);

    eventStream = new TestEventStream(samples.iterator());
    assertEquals(false, eventStream.hasNext());
  }
}