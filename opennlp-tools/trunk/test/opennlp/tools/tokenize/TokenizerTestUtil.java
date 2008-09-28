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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * Utility class for testing the {@link Tokenizer}.
 */
public class TokenizerTestUtil {

  static TokenizerModel createMaxentTokenModel() throws IOException {
    List<TokenSample> samples = new ArrayList<TokenSample>();
    
    samples.add(new TokenSample("year", new Span[]{new Span(0, 4)}));
    samples.add(new TokenSample("year,", new Span[]{
        new Span(0, 4),
        new Span(4, 5)}));
    samples.add(new TokenSample("it,", new Span[]{
        new Span(0, 2),
        new Span(2, 3)}));
    samples.add(new TokenSample("it", new Span[]{
        new Span(0, 2)}));
    samples.add(new TokenSample("yes", new Span[]{
        new Span(0, 3)}));
    samples.add(new TokenSample("yes,", new Span[]{
        new Span(0, 3),
        new Span(3, 4)}));
    
    return TokenizerME.train(samples.iterator(), true);
  }
  
}
