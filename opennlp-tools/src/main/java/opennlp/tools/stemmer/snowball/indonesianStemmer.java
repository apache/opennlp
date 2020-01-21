// CHECKSTYLE:OFF
/*

Copyright (c) 2010, Israel Olalla
Copyright (c) 2010, ISOCO
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

// This file was generated automatically by the Snowball to Java compiler

package opennlp.tools.stemmer.snowball;

/**
 * This class implements the stemming algorithm defined by a snowball script.
 */

public class indonesianStemmer extends opennlp.tools.stemmer.snowball.AbstractSnowballStemmer {

  private static final long serialVersionUID = 1L;

  public final static indonesianStemmer methodObject = new indonesianStemmer();

  private final static Among a_0[] = {
      new Among("kah", -1, 1, "", methodObject),
      new Among("lah", -1, 1, "", methodObject),
      new Among("pun", -1, 1, "", methodObject)
  };

  private final static Among a_1[] = {
      new Among("nya", -1, 1, "", methodObject),
      new Among("ku", -1, 1, "", methodObject),
      new Among("mu", -1, 1, "", methodObject)

  };

  private final static Among a_2[] = {
      new Among("i", -1, 1, "r_SUFFIX_I_OK", methodObject),
      new Among("an", -1, 1, "r_SUFFIX_AN_OK", methodObject),
      new Among("kan", 1, 1, "r_SUFFIX_KAN_OK", methodObject)
  };

  private final static Among a_3[] = {
      new Among("di", -1, 1, "", methodObject),
      new Among("ke", -1, 2, "", methodObject),
      new Among("me", -1, 1, "", methodObject),
      new Among("mem", 2, 5, "", methodObject),
      new Among("men", 2, 1, "", methodObject),
      new Among("meng", 4, 1, "", methodObject),
      new Among("meny", 4, 3, "r_VOWEL", methodObject),
      new Among("pem", -1, 6, "", methodObject),
      new Among("pen", -1, 2, "", methodObject),
      new Among("peng", 8, 2, "", methodObject),
      new Among("peny", 8, 4, "r_VOWEL", methodObject),
      new Among("ter", -1, 1, "", methodObject)
  };

  private final static Among a_4[] = {
      new Among("be", -1, 3, "r_KER", methodObject),
      new Among("belajar", 0, 4, "", methodObject),
      new Among("ber", 0, 3, "", methodObject),
      new Among("pe", -1, 1, "", methodObject),
      new Among("pelajar", 3, 2, "", methodObject),
      new Among("per", 3, 1, "", methodObject)
  };

  private static final char g_vowel[] = {17, 65, 16};

  private int I_measure = 0;
  private int I_prefix = 0;

  private void copy_from(indonesianStemmer other) {
    I_measure = other.I_measure;
    I_prefix = other.I_prefix;
    super.copy_from(other);
  }

  private boolean r_remove_particle() {
    int among_var;

    // (, line 50
    // [, line 51
    ket = cursor;

    // substring, line 51
    among_var = find_among_b(a_0);
    if (among_var == 0) {
      return false;
    }

    // ], line 51
    bra = cursor;

    // (, line 52
    // delete, line 52
    slice_del();

    I_measure--;
    return true;
  }

  private boolean r_remove_possessive_pronoun() {
    int among_var;

    // (, line 56
    // [, line 57
    ket = cursor;

    // substring, line 57
    among_var = find_among_b(a_1);
    if (among_var == 0) {
      return false;
    }

    // ], line 57
    bra = cursor;

    // ], line 58
    // delete, line 58
    slice_del();

    I_measure--;
    return true;
  }

  protected boolean r_SUFFIX_KAN_OK() {
    // (, line 63
    // and, line 85
    if (I_prefix == 3 || I_prefix == 2) {
      return false;
    }
    return true;
  }

  protected boolean r_SUFFIX_AN_OK() {
    // (, line 89
    if (I_prefix == 1) {
      return false;
    }
    return true;
  }

  protected boolean r_SUFFIX_I_OK() {
    int v_1;

    // line (, 91
    if (!(I_prefix <= 2)) {
      return false;
    }

    // not, line 128
    v_1 = limit - cursor;
    lab0:
    do {
      // literal, line 128
      if (!eq_s_b("s")) {
        break lab0;
      }
      return false;
    } while (false);

    cursor = limit - v_1;
    return true;
  }

  private boolean r_remove_suffix() {
    int among_var;

    // (, line 131
    // [, line 132
    ket = cursor;

    // substring, line 132
    among_var = find_among_b(a_2);
    if (among_var == 0) {
      return false;
    }

    // ], line 132
    bra = cursor;

    // (, line 134
    // delete, line 134
    slice_del();

    I_measure--;
    return true;
  }

  private boolean r_VOWEL() {
    // (, line 141
    if (!in_grouping(g_vowel, 97, 117)) {
      return false;
    }
    return true;
  }

  private boolean r_KER() {
    // (, line 143
    if (!out_grouping(g_vowel, 97, 117)) {
      return false;
    }

    // literal, line 143
    if (!eq_s("er".length(), "er")) {
      return false;
    }
    return true;
  }

  private boolean r_remove_first_order_prefix() {
    int among_var;

    // (, line 145
    // [, line 146
    bra = cursor;

    // substring, line 146
    among_var = find_among(a_3);
    if (among_var == 0) {
      return false;
    }
    // ], line 146
    ket = cursor;
    switch (among_var) {
      case 1:
        // (, line 147
        // delete, line 147
        slice_del();

        I_prefix = 1;
        I_measure--;
        break;

      case 2:
        // (, line 148
        // delete, line 148
        slice_del();
        I_prefix = 3;
        I_measure--;
        break;

      case 3:
        // (, line 149
        I_prefix = 1;

        // <-, line 149
        slice_from("s");
        I_measure--;
        break;

      case 4:
        // (, line 150
        I_prefix = 3;

        // <-, line 150
        slice_from("s");
        I_measure--;
        break;

      case 5:
        int v_1;
        int v_2;

        // (, line 151
        I_prefix = 1;
        I_measure--;

        // or, line 151
        lab0:
        do {
          v_1 = cursor;
          lab1:
          do {
            v_2 = cursor;
            if (!in_grouping(g_vowel, 97, 117)) {
              break lab1;
            }
            cursor = v_2;

            // <-, line 151
            slice_from("p");
            break lab0;
          } while (false);
          cursor = v_1;

          // delete, line 151
          slice_del();
        } while (false);
        break;

      case 6:
        int v_3;
        int v_4;

        // (, line 152
        I_prefix = 3;
        I_measure--;

        // or, line 152
        lab2:
        do {
          v_3 = cursor;
          lab3:
          do {
            // and, line 152
            v_4 = cursor;
            if (!in_grouping(g_vowel, 97, 117)) {
              break lab3;
            }
            cursor = v_4;

            // <-, line 152
            slice_from("p");
            break lab2;
          } while (false);
          cursor = v_3;

          // delete, line 152
          slice_del();
        } while (false);
        break;
    }
    return true;
  }

  private boolean r_remove_second_order_prefix() {
    int among_var;

    // (, line 156
    // [, line 162
    bra = cursor;

    // substring, line 162
    among_var = find_among(a_4);
    if (among_var == 0) {
      return false;
    }

    // ], line 162
    ket = cursor;
    switch (among_var) {
      case 1:
        // (, delete 163
        // delete, line 163
        slice_del();

        I_prefix = 2;
        I_measure--;
        break;

      case 2:
        // (, line 164
        // <-, line 164
        slice_from("ajar");

        I_measure--;
        break;

      case 3:
        // (, line 165
        // delete, line 165
        slice_del();

        I_prefix = 4;
        I_measure--;
        break;

      case 4:
        // (, line 166
        // <-, line 166
        slice_from("ajar");

        I_prefix = 4;
        I_measure--;
        break;
    }
    return true;
  }


  @Override
  public boolean stem() {
    int v_1;
    int v_2;
    int v_3;
    int v_4;
    int v_5;
    int v_6;
    int v_7;
    int v_8;
    int v_9;
    int v_10;


    // (, line 171
    I_measure = 0;

    // do, line 173
    v_1 = cursor;
    lab0:
    do {
      // (, line 173
      // repeat, line 173
      lab1:
      do {
        while (true) {
          lab2:
          do {
            v_2 = cursor;
            lab3:
            do {
              // (, line 173
              // gopast, line 173
              lab4:
              do {
                while (true) {
                  lab5:
                  do {
                    if (!in_grouping(g_vowel, 97, 117)) {
                      break lab5;
                    }
                    break lab4;
                  } while (false);
                  if (cursor >= limit) {
                    break lab3;
                  }
                  cursor++;
                }
              } while (false);
              I_measure++;
              break lab2;
            } while (false);
            cursor = v_2;
            break lab1;
          } while (false);
        }
      } while (false);
    } while (false);

    cursor = v_1;
    if (!(I_measure > 2)) {
      return false;
    }
    I_prefix = 0;
    // backwards, line 176
    limit_backward = cursor;
    cursor = limit;

    // (, line 176
    // do, line 177
    v_3 = limit - cursor;
    lab6:
    do {
      // call remove particle, line 177
      if (r_remove_particle()) {
        break lab6;
      }
    } while (false);
    cursor = limit - v_3;
    if (!(I_measure > 2)) {
      return false;
    }

    // do, line 179
    v_4 = limit - cursor;
    lab7:
    do {
      // call remove_possessive_pronoun, line 179
      if (r_remove_possessive_pronoun()) {
        break lab7;
      }
    } while (false);
    cursor = limit - v_4;
    cursor = limit_backward;
    if (!(I_measure > 2)) {
      return false;
    }
    // or, line 188
    lab8:
    do {
      v_5 = cursor;
      lab9:
      do {
        // test, line 182
        v_6 = cursor;

        // (, line 182
        // call remove_first_order_prefix, line 183
        if (!r_remove_first_order_prefix()) {
          break lab9;
        }
        // do, line 184
        v_7 = cursor;
        lab10:
        do {
          // (, line 184
          // test, line 185
          v_8 = cursor;

          // (, line 185
          if (!(I_measure > 2)) {
            break lab10;
          }

          // backwards, line 185
          limit_backward = cursor;
          cursor = limit;

          // call remove_suffix, line 185
          if (!r_remove_suffix()) {
            break lab10;
          }
          cursor = limit_backward;
          cursor = v_8;
          if (!(I_measure > 2)) {
            break lab10;
          }

          // call remove_second_order_prefix, line 186
          if (!r_remove_second_order_prefix()) {
            break lab10;
          }
        } while (false);
        cursor = v_7;
        cursor = v_6;
        break lab8;
      } while (false);
      cursor = v_5;
      // (, line 188
      // do, line 189
      v_9 = cursor;
      lab11:
      do {
        // call remove_second_order_prefix, line 189
        if (!r_remove_second_order_prefix()) {
          break lab11;
        }
      } while (false);
      cursor = v_9;

      // do, line 190
      v_10 = cursor;
      lab12:
      do {
        // (, line 190
        if (!(I_measure > 2)) {
          break lab12;
        }

        // backwards, line 190
        limit_backward = cursor;
        cursor = limit;

        // call remove_suffix, line 190
        if (!r_remove_suffix()) {
          break lab12;
        }
        cursor = limit_backward;
      } while (false);
      cursor = v_10;
    } while (false);
    return true;
  }

  public boolean equals(Object o) {
    return o instanceof indonesianStemmer;
  }

  public int hashCode() {
    return indonesianStemmer.class.getName().hashCode();
  }
}
