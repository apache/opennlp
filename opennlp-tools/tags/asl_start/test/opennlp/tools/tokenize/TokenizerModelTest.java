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

package opennlp.tools.tokenize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;
import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link TokenizerModel} class. 
 */
public class TokenizerModelTest extends TestCase {
  
  public void testSentenceModel() throws IOException, InvalidFormatException {
    
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    
    ByteArrayOutputStream arrayOut = new ByteArrayOutputStream();
    model.serialize(arrayOut);
    arrayOut.close();
    
    TokenizerModel.create(new ByteArrayInputStream(arrayOut.toByteArray()));
    
    // TODO: check that both maxent models are equal
  }
}