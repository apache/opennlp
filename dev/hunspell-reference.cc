// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <iostream>
#include <string>

extern "C" {
void* Hunspell_create(const char*, const char*);
void Hunspell_destroy(void*);
int Hunspell_stem(void*, char***, const char*);
int Hunspell_analyze(void*, char***, const char*);
int Hunspell_spell(void*, const char*);
void Hunspell_free_list(void*, char***, int);
}

// Prints one native result per input line; multiple stems use tab separators.
int main(int argc, char** argv) {
  if (argc != 4) return 2;
  const std::string operation(argv[3]);
  if (operation != "spell" && operation != "stem" && operation != "analyze") return 2;
  void* handle = Hunspell_create(argv[1], argv[2]);
  if (!handle) return 3;
  std::string word;
  while (std::getline(std::cin, word)) {
    char** values = nullptr;
    if (operation == "spell") {
      std::cout << Hunspell_spell(handle, word.c_str());
    } else {
      const int count = operation == "analyze"
          ? Hunspell_analyze(handle, &values, word.c_str())
          : Hunspell_stem(handle, &values, word.c_str());
      for (int i = 0; i < count; ++i) {
        if (i) std::cout << '\t';
        std::cout << values[i];
      }
      Hunspell_free_list(handle, &values, count);
    }
    std::cout << '\n';
  }
  Hunspell_destroy(handle);
}
