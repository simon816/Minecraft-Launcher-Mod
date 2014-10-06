Minecraft Launcher Modification
===============================

This 'hack' allows the vanilla launcher to be modified by a single patch to the main class.

It is really an experiment to play about with reflection among others but I use this as my launcher to Minecraft.

Features
--------
* Select Minecraft Forge builds direct in the launcher's profile selector
![Profile Editor](http://puu.sh/c1Tgu/4f0ae3b022.png)  
You can check the "Enable Forge Versions" checkbox to get a list.  
It also provides a checkbox for standard vanilla releases to be toggled.

* Can download and play _almost_ any forge version. Forge has changed a lot, especially in how it is installed.
Depending on what version you select, ModLoader may be downloaded or various other install procedures take place.   

* See the mods you have in the mods folder. This is a very basic feature that may be improved.
![Mod Tab](http://puu.sh/c1TmG/faed6b5cac.png)


Developing
----------
#### Prerequisites
 * Eclipse
 * Gradle

 1. You will need to decompile your Minecraft.jar that you launch the game with.  
How you do this is up to you, I am unable to share the sources.
 2. Apply the patch [Bootstrap.java.patch](./patches/net/minecraft/bootstrap/Bootstrap.java.patch) (You may have to do this manually).
 3. Copy all files (i.e. `git clone`) to wherever the decompiled sources are.
 4. Run `gradle eclipse` in that somewhere to setup the project.
 5. Import the project in eclipse.
 6. Move files to where they should be for a gradle project and fix any errors the decompiler made.
 7. Setup a Run Configuration to run `net.minecraft.bootstrap.Bootstrap`

Everything _should_ work.

#### Additionally:
You can run `gradle build` which will output:
* `build/libs/Minecraft.jar` - A rebuild launcher jar. __DO NOT DISTRIBUTE__
* `build/patches/*` - Compiled classes that can be copied into a Minecraft.jar
