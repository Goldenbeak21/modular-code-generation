package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.AutomataInstance

/**
 * The class that contains methods to do with the generation of the MakeFile for the network
 */
object MakefileGenerator {
    private var instances: Map<String, AutomataInstance> = LinkedHashMap()
    private var config: Configuration = Configuration()

    /**
     * Generates a string that represents the Makefile for the network. The final generated program will be named by the
     * provided networkName.
     */
    fun generate(networkName: String, instances: Map<String, AutomataInstance>, config: Configuration = Configuration()): String {
        this.instances = instances
        this.config = config

        val result = StringBuilder()

        // The final target is set by the networkName
        result.appendln("TARGET = $networkName")

        // Default compiler settings, using gcc
        result.appendln("CC = gcc")
        result.appendln("CFLAGS = -c -O2 -Wall")
        result.appendln("LDFLAGS = -g -Wall")
        result.appendln("LDLIBS = -lm")
        result.appendln()

        // The default build target is to build the executable
        result.appendln("build: $(TARGET)")
        result.appendln()

        // We keep track of the sources for when we link at the end
        val sources = ArrayList<String>()

        // We can only generate code if there are any instances
        if(instances.isNotEmpty()) {
            // Depending on the parametrisation method, we'll do things slightly differently
            if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                // Compile time parametrisation means compiling each instance
                for((name, instance) in instances) {
                    // Generate the file name that we'll be looking for
                    val deliminatedName = Utils.createFileName(name)

                    // Generated the folder name that we'll be looking for
                    val subfolder = if(instance.automata.equals(networkName, true)) { instance.automata + " Files" } else { instance.automata }
                    val deliminatedFolder = Utils.createFolderName(subfolder)

                    // Create the compile command for the file
                    result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedFolder/$deliminatedName.c"), listOf("$deliminatedFolder/$deliminatedName.h", CCodeGenerator.CONFIG_FILE)))
                    result.appendln()

                    // Keep track of the sources
                    sources.add("Objects/$deliminatedName")
                }
            }
            else {
                // We only want to generate each definition once, so keep a track of them
                val generated = ArrayList<String>()
                for((_, instance) in instances) {
                    if (!generated.contains(instance.automata)) {
                        generated.add(instance.automata)

                        // Generate the file name that we'll be looking for
                        val deliminatedName = Utils.createFileName(instance.automata)

                        // Create the compile command for the file
                        result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedName.c"), listOf("$deliminatedName.h", CCodeGenerator.CONFIG_FILE)))
                        result.appendln()

                        // Keep track of the sources
                        sources.add("Objects/$deliminatedName")
                    }
                }
            }
        }

        // Create the compile command for the runnable main file
        result.append(generateCompileCommand("runnable", listOf("runnable.c"), listOf(CCodeGenerator.CONFIG_FILE)))
        result.appendln()

        // Keep track of the sources
        sources.add("Objects/runnable")

        // Generate the link command, with all the sources
        result.append(generateLinkCommand("$(TARGET)", sources))
        result.appendln()

        // Now we have the clean command, which deletes the target and all objects
        result.appendln(".PHONY: clean")
        result.appendln("clean:")
        result.appendln("\t@echo Removing compiled binaries...")
        result.appendln("\t@rm -rf $(TARGET) Objects/* *~")
        result.appendln()

        // Return the final Makefile
        return result.toString().trim()
    }

    /**
     * Generates a compile command for the given object, taking in the given sources and dependencies for use in the
     * command. Note that sources are automatically by default dependencies
     */
    private fun generateCompileCommand(name: String, sources: List<String>, dependencies: List<String>): String {
        val result = StringBuilder()

        // The output will be in the Objects directory
        result.append("Objects/$name:")
        // And we need to list all the dependencies we have (sources are also dependencies)
        for(source in sources) {
            result.append(" $source")
        }
        for(dependency in dependencies) {
            result.append(" $dependency")
        }
        result.appendln()

        // Let the user know what it's currently compiling
        result.appendln("\t@echo Building $name...")
        // And then compile (making the directory if needed)
        result.append("\t@mkdir -p Objects; $(CC) $(CFLAGS)")
        if(sources.size == 1) {
            // If there's only one source then we use the shorthand for Makefile sources
            result.append(" $<")
        }
        else {
            // Otherwise let's just list them all
            for (source in sources) {
                result.append(" $source")
            }
        }
        // The output also uses the Makefile shorthand
        result.appendln(" -o $@")

        // Return the compile command
        return result.toString()
    }

    /**
     * Generates a linker command for the program, taking in the given sources
     */
    private fun generateLinkCommand(output: String, sources: List<String>): String {
        val result = StringBuilder()

        // Output file is the name of this task
        result.append("$output:")
        // It depends on all of the sources
        for(source in sources) {
            result.append(" $source")
        }
        result.appendln()

        // Let the user know what it's currently linking
        result.appendln("\t@echo Building $output...")
        // And then link, using the Makefile shorthands for source and output files
        result.append("\t$(CC) $(LDFLAGS) $^ $(LDLIBS) -o $@")

        // Return the linker command
        return result.toString()
    }
}