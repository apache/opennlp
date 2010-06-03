///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2010 OpenNlp
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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import opennlp.model.Event;
import opennlp.model.EventStream;

public class HashSumEventStream implements EventStream {

  private final EventStream eventStream;
  
  private MessageDigest digest;
  
  public HashSumEventStream(EventStream eventStream) {
    this.eventStream = eventStream;
    
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // should never happen, does all java runtimes have md5 ?!
      // no, if nor throw a meaningful error
      e.printStackTrace();
    }
  }
  
  public boolean hasNext() {
    return eventStream.hasNext();
  }

  public Event next() {
    
    Event event = eventStream.next();
    
    try {
      digest.update(event.toString().getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 encoding is not available!");
    }
    
    return event;
  }
  
  /**
   * Calculates the hash sum of the stream. The method must be
   * called after the stream is completely consumed.
   * 
   * @return the hash sum
   * @throws IllegalStateException if the stream is not consumed completely,
   * completely means that hasNext() returns false
   */
  public BigInteger calculateHashSum() {
    if (hasNext())
      throw new IllegalStateException("stream must be consumed completely!");
    
    return new BigInteger(1, digest.digest());
  }
  
  public void remove() {
  }
}
