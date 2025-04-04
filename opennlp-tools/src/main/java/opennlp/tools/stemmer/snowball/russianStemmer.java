// CHECKSTYLE:OFF
/*

Copyright (c) 2001, Dr Martin Porter
Copyright (c) 2002, Richard Boulton
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
    * this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
    * notice, this list of conditions and the following disclaimer in the
    * documentation and/or other materials provided with the distribution.
    * Neither the name of the copyright holders nor the names of its contributors
    * may be used to endorse or promote products derived from this software
    * without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

// Generated by Snowball (build from 9a22f0d3f44cda36677829328fe2642750114d57)
package opennlp.tools.stemmer.snowball;


/**
 * This class implements the stemming algorithm defined by a snowball script.
 * <p>
 * Generated by Snowball (build from 9a22f0d3f44cda36677829328fe2642750114d57) - <a href="https://github.com/snowballstem/snowball">https://github.com/snowballstem/snowball</a>
 * </p>
 */
@SuppressWarnings("unused")
public class russianStemmer extends AbstractSnowballStemmer {

  private static final long serialVersionUID = 1L;

  private final static Among[] a_0 = {
      new Among("\u0432", -1, 1),
      new Among("\u0438\u0432", 0, 2),
      new Among("\u044B\u0432", 0, 2),
      new Among("\u0432\u0448\u0438", -1, 1),
      new Among("\u0438\u0432\u0448\u0438", 3, 2),
      new Among("\u044B\u0432\u0448\u0438", 3, 2),
      new Among("\u0432\u0448\u0438\u0441\u044C", -1, 1),
      new Among("\u0438\u0432\u0448\u0438\u0441\u044C", 6, 2),
      new Among("\u044B\u0432\u0448\u0438\u0441\u044C", 6, 2)
  };

  private final static Among[] a_1 = {
      new Among("\u0435\u0435", -1, 1),
      new Among("\u0438\u0435", -1, 1),
      new Among("\u043E\u0435", -1, 1),
      new Among("\u044B\u0435", -1, 1),
      new Among("\u0438\u043C\u0438", -1, 1),
      new Among("\u044B\u043C\u0438", -1, 1),
      new Among("\u0435\u0439", -1, 1),
      new Among("\u0438\u0439", -1, 1),
      new Among("\u043E\u0439", -1, 1),
      new Among("\u044B\u0439", -1, 1),
      new Among("\u0435\u043C", -1, 1),
      new Among("\u0438\u043C", -1, 1),
      new Among("\u043E\u043C", -1, 1),
      new Among("\u044B\u043C", -1, 1),
      new Among("\u0435\u0433\u043E", -1, 1),
      new Among("\u043E\u0433\u043E", -1, 1),
      new Among("\u0435\u043C\u0443", -1, 1),
      new Among("\u043E\u043C\u0443", -1, 1),
      new Among("\u0438\u0445", -1, 1),
      new Among("\u044B\u0445", -1, 1),
      new Among("\u0435\u044E", -1, 1),
      new Among("\u043E\u044E", -1, 1),
      new Among("\u0443\u044E", -1, 1),
      new Among("\u044E\u044E", -1, 1),
      new Among("\u0430\u044F", -1, 1),
      new Among("\u044F\u044F", -1, 1)
  };

  private final static Among[] a_2 = {
      new Among("\u0435\u043C", -1, 1),
      new Among("\u043D\u043D", -1, 1),
      new Among("\u0432\u0448", -1, 1),
      new Among("\u0438\u0432\u0448", 2, 2),
      new Among("\u044B\u0432\u0448", 2, 2),
      new Among("\u0449", -1, 1),
      new Among("\u044E\u0449", 5, 1),
      new Among("\u0443\u044E\u0449", 6, 2)
  };

  private final static Among[] a_3 = {
      new Among("\u0441\u044C", -1, 1),
      new Among("\u0441\u044F", -1, 1)
  };

  private final static Among[] a_4 = {
      new Among("\u043B\u0430", -1, 1),
      new Among("\u0438\u043B\u0430", 0, 2),
      new Among("\u044B\u043B\u0430", 0, 2),
      new Among("\u043D\u0430", -1, 1),
      new Among("\u0435\u043D\u0430", 3, 2),
      new Among("\u0435\u0442\u0435", -1, 1),
      new Among("\u0438\u0442\u0435", -1, 2),
      new Among("\u0439\u0442\u0435", -1, 1),
      new Among("\u0435\u0439\u0442\u0435", 7, 2),
      new Among("\u0443\u0439\u0442\u0435", 7, 2),
      new Among("\u043B\u0438", -1, 1),
      new Among("\u0438\u043B\u0438", 10, 2),
      new Among("\u044B\u043B\u0438", 10, 2),
      new Among("\u0439", -1, 1),
      new Among("\u0435\u0439", 13, 2),
      new Among("\u0443\u0439", 13, 2),
      new Among("\u043B", -1, 1),
      new Among("\u0438\u043B", 16, 2),
      new Among("\u044B\u043B", 16, 2),
      new Among("\u0435\u043C", -1, 1),
      new Among("\u0438\u043C", -1, 2),
      new Among("\u044B\u043C", -1, 2),
      new Among("\u043D", -1, 1),
      new Among("\u0435\u043D", 22, 2),
      new Among("\u043B\u043E", -1, 1),
      new Among("\u0438\u043B\u043E", 24, 2),
      new Among("\u044B\u043B\u043E", 24, 2),
      new Among("\u043D\u043E", -1, 1),
      new Among("\u0435\u043D\u043E", 27, 2),
      new Among("\u043D\u043D\u043E", 27, 1),
      new Among("\u0435\u0442", -1, 1),
      new Among("\u0443\u0435\u0442", 30, 2),
      new Among("\u0438\u0442", -1, 2),
      new Among("\u044B\u0442", -1, 2),
      new Among("\u044E\u0442", -1, 1),
      new Among("\u0443\u044E\u0442", 34, 2),
      new Among("\u044F\u0442", -1, 2),
      new Among("\u043D\u044B", -1, 1),
      new Among("\u0435\u043D\u044B", 37, 2),
      new Among("\u0442\u044C", -1, 1),
      new Among("\u0438\u0442\u044C", 39, 2),
      new Among("\u044B\u0442\u044C", 39, 2),
      new Among("\u0435\u0448\u044C", -1, 1),
      new Among("\u0438\u0448\u044C", -1, 2),
      new Among("\u044E", -1, 2),
      new Among("\u0443\u044E", 44, 2)
  };

  private final static Among[] a_5 = {
      new Among("\u0430", -1, 1),
      new Among("\u0435\u0432", -1, 1),
      new Among("\u043E\u0432", -1, 1),
      new Among("\u0435", -1, 1),
      new Among("\u0438\u0435", 3, 1),
      new Among("\u044C\u0435", 3, 1),
      new Among("\u0438", -1, 1),
      new Among("\u0435\u0438", 6, 1),
      new Among("\u0438\u0438", 6, 1),
      new Among("\u0430\u043C\u0438", 6, 1),
      new Among("\u044F\u043C\u0438", 6, 1),
      new Among("\u0438\u044F\u043C\u0438", 10, 1),
      new Among("\u0439", -1, 1),
      new Among("\u0435\u0439", 12, 1),
      new Among("\u0438\u0435\u0439", 13, 1),
      new Among("\u0438\u0439", 12, 1),
      new Among("\u043E\u0439", 12, 1),
      new Among("\u0430\u043C", -1, 1),
      new Among("\u0435\u043C", -1, 1),
      new Among("\u0438\u0435\u043C", 18, 1),
      new Among("\u043E\u043C", -1, 1),
      new Among("\u044F\u043C", -1, 1),
      new Among("\u0438\u044F\u043C", 21, 1),
      new Among("\u043E", -1, 1),
      new Among("\u0443", -1, 1),
      new Among("\u0430\u0445", -1, 1),
      new Among("\u044F\u0445", -1, 1),
      new Among("\u0438\u044F\u0445", 26, 1),
      new Among("\u044B", -1, 1),
      new Among("\u044C", -1, 1),
      new Among("\u044E", -1, 1),
      new Among("\u0438\u044E", 30, 1),
      new Among("\u044C\u044E", 30, 1),
      new Among("\u044F", -1, 1),
      new Among("\u0438\u044F", 33, 1),
      new Among("\u044C\u044F", 33, 1)
  };

  private final static Among[] a_6 = {
      new Among("\u043E\u0441\u0442", -1, 1),
      new Among("\u043E\u0441\u0442\u044C", -1, 1)
  };

  private final static Among[] a_7 = {
      new Among("\u0435\u0439\u0448\u0435", -1, 1),
      new Among("\u043D", -1, 2),
      new Among("\u0435\u0439\u0448", -1, 1),
      new Among("\u044C", -1, 3)
  };

  private static final char[] g_v = {33, 65, 8, 232};

  private int I_p2;
  private int I_pV;


  private boolean r_mark_regions() {
    I_pV = limit;
    I_p2 = limit;
    int v_1 = cursor;
    lab0:
    {
      golab1:
      while (true) {
        lab2:
        {
          if (!(in_grouping(g_v, 1072, 1103))) {
            break lab2;
          }
          break golab1;
        }
        if (cursor >= limit) {
          break lab0;
        }
        cursor++;
      }
      I_pV = cursor;
      golab3:
      while (true) {
        lab4:
        {
          if (!(out_grouping(g_v, 1072, 1103))) {
            break lab4;
          }
          break golab3;
        }
        if (cursor >= limit) {
          break lab0;
        }
        cursor++;
      }
      golab5:
      while (true) {
        lab6:
        {
          if (!(in_grouping(g_v, 1072, 1103))) {
            break lab6;
          }
          break golab5;
        }
        if (cursor >= limit) {
          break lab0;
        }
        cursor++;
      }
      golab7:
      while (true) {
        lab8:
        {
          if (!(out_grouping(g_v, 1072, 1103))) {
            break lab8;
          }
          break golab7;
        }
        if (cursor >= limit) {
          break lab0;
        }
        cursor++;
      }
      I_p2 = cursor;
    }
    cursor = v_1;
    return true;
  }

  private boolean r_R2() {
    return I_p2 <= cursor;
  }

  private boolean r_perfective_gerund() {
    int among_var;
    ket = cursor;
    among_var = find_among_b(a_0);
    if (among_var == 0) {
      return false;
    }
    bra = cursor;
    switch (among_var) {
      case 1:
        lab0:
        {
          int v_1 = limit - cursor;
          lab1:
          {
            if (!(eq_s_b("\u0430"))) {
              break lab1;
            }
            break lab0;
          }
          cursor = limit - v_1;
          if (!(eq_s_b("\u044F"))) {
            return false;
          }
        }
        slice_del();
        break;
      case 2:
        slice_del();
        break;
    }
    return true;
  }

  private boolean r_adjective() {
    ket = cursor;
    if (find_among_b(a_1) == 0) {
      return false;
    }
    bra = cursor;
    slice_del();
    return true;
  }

  private boolean r_adjectival() {
    int among_var;
    if (!r_adjective()) {
      return false;
    }
    int v_1 = limit - cursor;
    lab0:
    {
      ket = cursor;
      among_var = find_among_b(a_2);
      if (among_var == 0) {
        cursor = limit - v_1;
        break lab0;
      }
      bra = cursor;
      switch (among_var) {
        case 1:
          lab1:
          {
            int v_2 = limit - cursor;
            lab2:
            {
              if (!(eq_s_b("\u0430"))) {
                break lab2;
              }
              break lab1;
            }
            cursor = limit - v_2;
            if (!(eq_s_b("\u044F"))) {
              cursor = limit - v_1;
              break lab0;
            }
          }
          slice_del();
          break;
        case 2:
          slice_del();
          break;
      }
    }
    return true;
  }

  private boolean r_reflexive() {
    ket = cursor;
    if (find_among_b(a_3) == 0) {
      return false;
    }
    bra = cursor;
    slice_del();
    return true;
  }

  private boolean r_verb() {
    int among_var;
    ket = cursor;
    among_var = find_among_b(a_4);
    if (among_var == 0) {
      return false;
    }
    bra = cursor;
    switch (among_var) {
      case 1:
        lab0:
        {
          int v_1 = limit - cursor;
          lab1:
          {
            if (!(eq_s_b("\u0430"))) {
              break lab1;
            }
            break lab0;
          }
          cursor = limit - v_1;
          if (!(eq_s_b("\u044F"))) {
            return false;
          }
        }
        slice_del();
        break;
      case 2:
        slice_del();
        break;
    }
    return true;
  }

  private boolean r_noun() {
    ket = cursor;
    if (find_among_b(a_5) == 0) {
      return false;
    }
    bra = cursor;
    slice_del();
    return true;
  }

  private boolean r_derivational() {
    ket = cursor;
    if (find_among_b(a_6) == 0) {
      return false;
    }
    bra = cursor;
    if (!r_R2()) {
      return false;
    }
    slice_del();
    return true;
  }

  private boolean r_tidy_up() {
    int among_var;
    ket = cursor;
    among_var = find_among_b(a_7);
    if (among_var == 0) {
      return false;
    }
    bra = cursor;
    switch (among_var) {
      case 1:
        slice_del();
        ket = cursor;
        if (!(eq_s_b("\u043D"))) {
          return false;
        }
        bra = cursor;
        if (!(eq_s_b("\u043D"))) {
          return false;
        }
        slice_del();
        break;
      case 2:
        if (!(eq_s_b("\u043D"))) {
          return false;
        }
        slice_del();
        break;
      case 3:
        slice_del();
        break;
    }
    return true;
  }

  @Override
  public boolean stem() {
    int v_1 = cursor;
    lab0:
    {
      while (true) {
        int v_2 = cursor;
        lab1:
        {
          golab2:
          while (true) {
            int v_3 = cursor;
            lab3:
            {
              bra = cursor;
              if (!(eq_s("\u0451"))) {
                break lab3;
              }
              ket = cursor;
              cursor = v_3;
              break golab2;
            }
            cursor = v_3;
            if (cursor >= limit) {
              break lab1;
            }
            cursor++;
          }
          slice_from("\u0435");
          continue;
        }
        cursor = v_2;
        break;
      }
    }
    cursor = v_1;
    r_mark_regions();
    limit_backward = cursor;
    cursor = limit;
    if (cursor < I_pV) {
      return false;
    }
    int v_6 = limit_backward;
    limit_backward = I_pV;
    int v_7 = limit - cursor;
    lab4:
    {
      lab5:
      {
        int v_8 = limit - cursor;
        lab6:
        {
          if (!r_perfective_gerund()) {
            break lab6;
          }
          break lab5;
        }
        cursor = limit - v_8;
        int v_9 = limit - cursor;
        lab7:
        {
          if (!r_reflexive()) {
            cursor = limit - v_9;
            break lab7;
          }
        }
        lab8:
        {
          int v_10 = limit - cursor;
          lab9:
          {
            if (!r_adjectival()) {
              break lab9;
            }
            break lab8;
          }
          cursor = limit - v_10;
          lab10:
          {
            if (!r_verb()) {
              break lab10;
            }
            break lab8;
          }
          cursor = limit - v_10;
          if (!r_noun()) {
            break lab4;
          }
        }
      }
    }
    cursor = limit - v_7;
    int v_11 = limit - cursor;
    lab11:
    {
      ket = cursor;
      if (!(eq_s_b("\u0438"))) {
        cursor = limit - v_11;
        break lab11;
      }
      bra = cursor;
      slice_del();
    }
    int v_12 = limit - cursor;
    r_derivational();
    cursor = limit - v_12;
    int v_13 = limit - cursor;
    r_tidy_up();
    cursor = limit - v_13;
    limit_backward = v_6;
    cursor = limit_backward;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof russianStemmer;
  }

  @Override
  public int hashCode() {
    return russianStemmer.class.getName().hashCode();
  }


}

