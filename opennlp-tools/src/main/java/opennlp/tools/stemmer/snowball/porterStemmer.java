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

// Generated by Snowball (build from 867c4ec70debd4daa7fb4d5a9f7759b47887d0b9)
package opennlp.tools.stemmer.snowball;


/**
 * This class implements the stemming algorithm defined by a snowball script.
 * <p>
 * Generated by Snowball (build from 867c4ec70debd4daa7fb4d5a9f7759b47887d0b9) - <a href="https://github.com/snowballstem/snowball">https://github.com/snowballstem/snowball</a>
 * </p>
 */
@SuppressWarnings("unused")
public class porterStemmer extends AbstractSnowballStemmer {


    private final static Among a_0[] = {
            new Among("s", -1, 3),
            new Among("ies", 0, 2),
            new Among("sses", 0, 1),
            new Among("ss", 0, -1)
    };

    private final static Among a_1[] = {
            new Among("", -1, 3),
            new Among("bb", 0, 2),
            new Among("dd", 0, 2),
            new Among("ff", 0, 2),
            new Among("gg", 0, 2),
            new Among("bl", 0, 1),
            new Among("mm", 0, 2),
            new Among("nn", 0, 2),
            new Among("pp", 0, 2),
            new Among("rr", 0, 2),
            new Among("at", 0, 1),
            new Among("tt", 0, 2),
            new Among("iz", 0, 1)
    };

    private final static Among a_2[] = {
            new Among("ed", -1, 2),
            new Among("eed", 0, 1),
            new Among("ing", -1, 2)
    };

    private final static Among a_3[] = {
            new Among("anci", -1, 3),
            new Among("enci", -1, 2),
            new Among("abli", -1, 4),
            new Among("eli", -1, 6),
            new Among("alli", -1, 9),
            new Among("ousli", -1, 11),
            new Among("entli", -1, 5),
            new Among("aliti", -1, 9),
            new Among("biliti", -1, 13),
            new Among("iviti", -1, 12),
            new Among("tional", -1, 1),
            new Among("ational", 10, 8),
            new Among("alism", -1, 9),
            new Among("ation", -1, 8),
            new Among("ization", 13, 7),
            new Among("izer", -1, 7),
            new Among("ator", -1, 8),
            new Among("iveness", -1, 12),
            new Among("fulness", -1, 10),
            new Among("ousness", -1, 11)
    };

    private final static Among a_4[] = {
            new Among("icate", -1, 2),
            new Among("ative", -1, 3),
            new Among("alize", -1, 1),
            new Among("iciti", -1, 2),
            new Among("ical", -1, 2),
            new Among("ful", -1, 3),
            new Among("ness", -1, 3)
    };

    private final static Among a_5[] = {
            new Among("ic", -1, 1),
            new Among("ance", -1, 1),
            new Among("ence", -1, 1),
            new Among("able", -1, 1),
            new Among("ible", -1, 1),
            new Among("ate", -1, 1),
            new Among("ive", -1, 1),
            new Among("ize", -1, 1),
            new Among("iti", -1, 1),
            new Among("al", -1, 1),
            new Among("ism", -1, 1),
            new Among("ion", -1, 2),
            new Among("er", -1, 1),
            new Among("ous", -1, 1),
            new Among("ant", -1, 1),
            new Among("ent", -1, 1),
            new Among("ment", 15, 1),
            new Among("ement", 16, 1),
            new Among("ou", -1, 1)
    };

    private static final char g_v[] = {17, 65, 16, 1};

    private static final char g_v_WXY[] = {1, 17, 65, 208, 1};

    private boolean B_Y_found;
    private int I_p2;
    private int I_p1;


    private boolean r_shortv() {
        if (!(out_grouping_b(g_v_WXY, 89, 121))) {
            return false;
        }
        if (!(in_grouping_b(g_v, 97, 121))) {
            return false;
        }
        if (!(out_grouping_b(g_v, 97, 121))) {
            return false;
        }
        return true;
    }

    private boolean r_R1() {
        return I_p1 <= cursor;
    }

    private boolean r_R2() {
        return I_p2 <= cursor;
    }

    private boolean r_Step_1a() {
        int among_var;
        ket = cursor;
        among_var = find_among_b(a_0);
        if (among_var == 0) {
            return false;
        }
        bra = cursor;
        switch (among_var) {
            case 1:
                slice_from("ss");
                break;
            case 2:
                slice_from("i");
                break;
            case 3:
                slice_del();
                break;
        }
        return true;
    }

    private boolean r_Step_1b() {
        int among_var;
        ket = cursor;
        among_var = find_among_b(a_2);
        if (among_var == 0) {
            return false;
        }
        bra = cursor;
        switch (among_var) {
            case 1:
                if (!r_R1()) {
                    return false;
                }
                slice_from("ee");
                break;
            case 2:
                int v_1 = limit - cursor;
                golab0:
                while (true) {
                    lab1:
                    {
                        if (!(in_grouping_b(g_v, 97, 121))) {
                            break lab1;
                        }
                        break golab0;
                    }
                    if (cursor <= limit_backward) {
                        return false;
                    }
                    cursor--;
                }
                cursor = limit - v_1;
                slice_del();
                int v_3 = limit - cursor;
                among_var = find_among_b(a_1);
                cursor = limit - v_3;
                switch (among_var) {
                    case 1: {
                        int c = cursor;
                        insert(cursor, cursor, "e");
                        cursor = c;
                    }
                    break;
                    case 2:
                        ket = cursor;
                        if (cursor <= limit_backward) {
                            return false;
                        }
                        cursor--;
                        bra = cursor;
                        slice_del();
                        break;
                    case 3:
                        if (cursor != I_p1) {
                            return false;
                        }
                        int v_4 = limit - cursor;
                        if (!r_shortv()) {
                            return false;
                        }
                        cursor = limit - v_4;
                    {
                        int c = cursor;
                        insert(cursor, cursor, "e");
                        cursor = c;
                    }
                    break;
                }
                break;
        }
        return true;
    }

    private boolean r_Step_1c() {
        ket = cursor;
        lab0:
        {
            int v_1 = limit - cursor;
            lab1:
            {
                if (!(eq_s_b("y"))) {
                    break lab1;
                }
                break lab0;
            }
            cursor = limit - v_1;
            if (!(eq_s_b("Y"))) {
                return false;
            }
        }
        bra = cursor;
        golab2:
        while (true) {
            lab3:
            {
                if (!(in_grouping_b(g_v, 97, 121))) {
                    break lab3;
                }
                break golab2;
            }
            if (cursor <= limit_backward) {
                return false;
            }
            cursor--;
        }
        slice_from("i");
        return true;
    }

    private boolean r_Step_2() {
        int among_var;
        ket = cursor;
        among_var = find_among_b(a_3);
        if (among_var == 0) {
            return false;
        }
        bra = cursor;
        if (!r_R1()) {
            return false;
        }
        switch (among_var) {
            case 1:
                slice_from("tion");
                break;
            case 2:
                slice_from("ence");
                break;
            case 3:
                slice_from("ance");
                break;
            case 4:
                slice_from("able");
                break;
            case 5:
                slice_from("ent");
                break;
            case 6:
                slice_from("e");
                break;
            case 7:
                slice_from("ize");
                break;
            case 8:
                slice_from("ate");
                break;
            case 9:
                slice_from("al");
                break;
            case 10:
                slice_from("ful");
                break;
            case 11:
                slice_from("ous");
                break;
            case 12:
                slice_from("ive");
                break;
            case 13:
                slice_from("ble");
                break;
        }
        return true;
    }

    private boolean r_Step_3() {
        int among_var;
        ket = cursor;
        among_var = find_among_b(a_4);
        if (among_var == 0) {
            return false;
        }
        bra = cursor;
        if (!r_R1()) {
            return false;
        }
        switch (among_var) {
            case 1:
                slice_from("al");
                break;
            case 2:
                slice_from("ic");
                break;
            case 3:
                slice_del();
                break;
        }
        return true;
    }

    private boolean r_Step_4() {
        int among_var;
        ket = cursor;
        among_var = find_among_b(a_5);
        if (among_var == 0) {
            return false;
        }
        bra = cursor;
        if (!r_R2()) {
            return false;
        }
        switch (among_var) {
            case 1:
                slice_del();
                break;
            case 2:
                lab0:
                {
                    int v_1 = limit - cursor;
                    lab1:
                    {
                        if (!(eq_s_b("s"))) {
                            break lab1;
                        }
                        break lab0;
                    }
                    cursor = limit - v_1;
                    if (!(eq_s_b("t"))) {
                        return false;
                    }
                }
                slice_del();
                break;
        }
        return true;
    }

    private boolean r_Step_5a() {
        ket = cursor;
        if (!(eq_s_b("e"))) {
            return false;
        }
        bra = cursor;
        lab0:
        {
            int v_1 = limit - cursor;
            lab1:
            {
                if (!r_R2()) {
                    break lab1;
                }
                break lab0;
            }
            cursor = limit - v_1;
            if (!r_R1()) {
                return false;
            }
            {
                int v_2 = limit - cursor;
                lab2:
                {
                    if (!r_shortv()) {
                        break lab2;
                    }
                    return false;
                }
                cursor = limit - v_2;
            }
        }
        slice_del();
        return true;
    }

    private boolean r_Step_5b() {
        ket = cursor;
        if (!(eq_s_b("l"))) {
            return false;
        }
        bra = cursor;
        if (!r_R2()) {
            return false;
        }
        if (!(eq_s_b("l"))) {
            return false;
        }
        slice_del();
        return true;
    }

    public boolean stem() {
        B_Y_found = false;
        int v_1 = cursor;
        lab0:
        {
            bra = cursor;
            if (!(eq_s("y"))) {
                break lab0;
            }
            ket = cursor;
            slice_from("Y");
            B_Y_found = true;
        }
        cursor = v_1;
        int v_2 = cursor;
        lab1:
        {
            while (true) {
                int v_3 = cursor;
                lab2:
                {
                    golab3:
                    while (true) {
                        int v_4 = cursor;
                        lab4:
                        {
                            if (!(in_grouping(g_v, 97, 121))) {
                                break lab4;
                            }
                            bra = cursor;
                            if (!(eq_s("y"))) {
                                break lab4;
                            }
                            ket = cursor;
                            cursor = v_4;
                            break golab3;
                        }
                        cursor = v_4;
                        if (cursor >= limit) {
                            break lab2;
                        }
                        cursor++;
                    }
                    slice_from("Y");
                    B_Y_found = true;
                    continue;
                }
                cursor = v_3;
                break;
            }
        }
        cursor = v_2;
        I_p1 = limit;
        I_p2 = limit;
        int v_5 = cursor;
        lab5:
        {
            golab6:
            while (true) {
                lab7:
                {
                    if (!(in_grouping(g_v, 97, 121))) {
                        break lab7;
                    }
                    break golab6;
                }
                if (cursor >= limit) {
                    break lab5;
                }
                cursor++;
            }
            golab8:
            while (true) {
                lab9:
                {
                    if (!(out_grouping(g_v, 97, 121))) {
                        break lab9;
                    }
                    break golab8;
                }
                if (cursor >= limit) {
                    break lab5;
                }
                cursor++;
            }
            I_p1 = cursor;
            golab10:
            while (true) {
                lab11:
                {
                    if (!(in_grouping(g_v, 97, 121))) {
                        break lab11;
                    }
                    break golab10;
                }
                if (cursor >= limit) {
                    break lab5;
                }
                cursor++;
            }
            golab12:
            while (true) {
                lab13:
                {
                    if (!(out_grouping(g_v, 97, 121))) {
                        break lab13;
                    }
                    break golab12;
                }
                if (cursor >= limit) {
                    break lab5;
                }
                cursor++;
            }
            I_p2 = cursor;
        }
        cursor = v_5;
        limit_backward = cursor;
        cursor = limit;
        int v_10 = limit - cursor;
        r_Step_1a();
        cursor = limit - v_10;
        int v_11 = limit - cursor;
        r_Step_1b();
        cursor = limit - v_11;
        int v_12 = limit - cursor;
        r_Step_1c();
        cursor = limit - v_12;
        int v_13 = limit - cursor;
        r_Step_2();
        cursor = limit - v_13;
        int v_14 = limit - cursor;
        r_Step_3();
        cursor = limit - v_14;
        int v_15 = limit - cursor;
        r_Step_4();
        cursor = limit - v_15;
        int v_16 = limit - cursor;
        r_Step_5a();
        cursor = limit - v_16;
        int v_17 = limit - cursor;
        r_Step_5b();
        cursor = limit - v_17;
        cursor = limit_backward;
        int v_18 = cursor;
        lab14:
        {
            if (!(B_Y_found)) {
                break lab14;
            }
            while (true) {
                int v_19 = cursor;
                lab15:
                {
                    golab16:
                    while (true) {
                        int v_20 = cursor;
                        lab17:
                        {
                            bra = cursor;
                            if (!(eq_s("Y"))) {
                                break lab17;
                            }
                            ket = cursor;
                            cursor = v_20;
                            break golab16;
                        }
                        cursor = v_20;
                        if (cursor >= limit) {
                            break lab15;
                        }
                        cursor++;
                    }
                    slice_from("y");
                    continue;
                }
                cursor = v_19;
                break;
            }
        }
        cursor = v_18;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof porterStemmer;
    }

    @Override
    public int hashCode() {
        return porterStemmer.class.getName().hashCode();
    }


}

