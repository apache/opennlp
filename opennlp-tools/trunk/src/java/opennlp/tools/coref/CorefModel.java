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

package opennlp.tools.coref;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opennlp.model.MaxentModel;

public class CorefModel {
  
  private Set<String> maleNames;
  private Set<String> femaleNames;
  
  public CorefModel(String project) throws IOException {
    maleNames = readNames(project + "/gen.mas");
    femaleNames = readNames(project + "/gen.fem");
  }
  
  private static Set<String> readNames(String nameFile) throws IOException {
    Set<String> names = new HashSet<String>();
    
    BufferedReader nameReader = new BufferedReader(new FileReader(nameFile));
    for (String line = nameReader.readLine(); line != null; line = nameReader.readLine()) {
      names.add(line);
    }
    
    return names;
  }
  
  public Set<String> getMaleNames() {
    return maleNames;
  }
  
  public Set<String> getFemaleNames() {
    return femaleNames;
  }
  
  public MaxentModel getNumberModel() {
    return null;
  }
  
  public Map<String, Set<String>> getAcronyms() {
    return null;
  }
  
  public MaxentModel getCommonNounResolverModel() {
    // cmodel
    return null;
  }
  
  public MaxentModel getDefiniteNounResolverModel() {
    // defmodel
    return null;
  }
  
  public MaxentModel getSpeechPronounResolverModel() {
    // fmodel
    return null;
  }

  // Where is this model used ?
  public MaxentModel getIModel() {
    return null;
  }
  
  public MaxentModel getPluralNounResolverModel() {
    // plmodel
    return null;
  }

  public MaxentModel getSingularPronounResolverModel() {
    // pmodel
    return null;
  }

  public MaxentModel getProperNounResolverModel() {
    // pnmodel
    return null;
  }

  public MaxentModel getSimModel() {
    // simmodel
    return null;
  }

  public MaxentModel getPluralPronounResolverModel() {
    // tmodel
    return null;
  }
}