# Copyright 2021 Yuan He. All rights reserved.
from os.path import exists

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#     http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# the following code is credited to the mOWL library 
import jpype
import jpype.imports  # very important for basic Java dependencies!
import os
import glob
from pathlib import Path


def writelog(message):
    mode = "a" if exists("debug.txt") else "w"
    with open("debug.txt", mode, encoding='utf-8') as file:
        file.write(message)


""" Get jars from in-project dir """
def get_jars():
    jars_dir = str(Path(str(Path(__file__).parent) + '/align/logmap/java-dependencies'))
    jars = glob.glob(os.path.join(jars_dir, '*.jar'))
    print(f"Look in {jars_dir}\n# jars = {len(jars)}\njars list = {jars[0:5] if len(jars)>0 else len(jars)}\n")

    # Separator for linux is ":" while for windows ";" -> To fix unable to import java libs
    sep  = ":" if jars_dir.startswith("/KnowledgeGraphsApp") else ";"
    jars = f'{str.join(sep, [str(Path(jar_file)) for jar_file in jars])}'
    return jars


def init_jvm(memory):

    if not jpype.isJVMStarted():
        jars = get_jars()
        jpype.startJVM(
            jpype.getDefaultJVMPath(), "-ea",
            f"-Xmx{memory}",
            "-Djava.class.path=" + jars,
            convertStrings=False)
        
    if jpype.isJVMStarted():
        print(f"{memory} maximum memory allocated to JVM.")
        print("JVM started successfully.")
