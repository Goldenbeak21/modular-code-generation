package me.nallen.modularCodeGeneration.codeGen.c

import me.nallen.modularCodeGeneration.codeGen.Configuration
import me.nallen.modularCodeGeneration.codeGen.ParametrisationMethod
import me.nallen.modularCodeGeneration.hybridAutomata.HybridItem
import me.nallen.modularCodeGeneration.hybridAutomata.HybridNetwork
import java.util.*

/**
 * The class that contains methods to do with the generation of the MakeFile for the network
 */
object MakefileGenerator {
    private var item: HybridItem = HybridNetwork()
    private var config: Configuration = Configuration()

    /**
     * Generates a string that represents the Makefile for the network. The final generated program will be named by the
     * provided networkName.
     */
    fun generate(item: HybridItem, config: Configuration, isRoot: Boolean): String {
        this.item = item
        this.config = config

        val result = StringBuilder()

        // The final target is set by the networkName
        if(isRoot)
            result.appendln("TARGET = ${Utils.createFileName(item.name)}")
        else
            result.appendln("TARGET = ${Utils.createFileName(item.name)}.a")

        // Default compiler settings, using gcc
        result.appendln("CC ?= gcc")
        result.appendln("BASEDIR ?= \$(shell pwd)")
        result.appendln("CFLAGS ?= -c -O2 -Wall -I\$(BASEDIR)")
        result.appendln("LDFLAGS ?= -g -Wall")
        result.appendln("LDLIBS ?= -lm")
        result.appendln("AR ?= ar")
        result.appendln("ARFLAGS ?= cr")
        result.appendln("AREXTRACT ?= x")
        result.appendln("OBJECTSDIR ?= Objects")
        result.appendln()

        result.appendln("export")
        result.appendln()

        // The default build target is to build the executable
        result.appendln("build: \$(TARGET)")
        result.appendln()

        // We keep track of the sources for when we link at the end
        val sources = ArrayList<String>()

        if(isRoot) {
            val deliminatedName = Utils.createFileName(item.name)
            val dependency = if(item is HybridNetwork) {
                result.appendln(".PHONY: ${Utils.createFolderName(item.name, "Network")}/$deliminatedName.a")
                result.appendln("${Utils.createFolderName(item.name, "Network")}/$deliminatedName.a:")
                result.appendln("\t@\$(MAKE) -C ${Utils.createFolderName(item.name, "Network")}/ $deliminatedName.a")
                result.appendln()

                "${Utils.createFolderName(item.name, "Network")}/$deliminatedName.a"
            } else {
                result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedName.c"), listOf("$deliminatedName.h", "\$(BASEDIR)/${CCodeGenerator.CONFIG_FILE}")))
                result.appendln()

                "Objects/$deliminatedName.o"
            }

            // Keep track of the sources
            sources.add(dependency)

            // Create the compile command for the runnable main file
            result.append(generateCompileCommand("runnable", listOf("runnable.c"), listOf("\$(BASEDIR)/${CCodeGenerator.CONFIG_FILE}")))
            result.appendln()

            result.appendln("\$(TARGET): \$(OBJECTSDIR)/runnable.o $dependency")

            // Let the user know what it's currently linking
            result.appendln("\t@echo Building \$(TARGET)...")
            // And then link, using the Makefile shorthands for source and output files
            result.appendln("\t@\$(CC) \$(LDFLAGS) $^ \$(LDLIBS) -o $@")
            result.appendln()
        }
        else {
            // We can only generate code if this is a HybridNetwork
            if(item is HybridNetwork) {
                // Depending on the parametrisation method, we'll do things slightly differently
                if(config.parametrisationMethod == ParametrisationMethod.COMPILE_TIME) {
                    // Compile time parametrisation means compiling each instantiate
                    for ((_, instance) in item.instances) {
                        val instantiate = item.getInstantiateForInstantiateId(instance.instantiate)
                        val definition = item.getDefinitionForInstantiateId(instance.instantiate)
                        if (instantiate != null && definition != null) {
                            // Generate the file name that we'll be looking for
                            val deliminatedName = Utils.createFileName(instantiate.name)

                            // Generated the folder name that we'll be looking for
                            val subfolder = if (definition.name.equals(item.name, true)) {
                                definition.name + " Files"
                            } else {
                                definition.name
                            }
                            val deliminatedFolder = Utils.createFolderName(subfolder)

                            val dependency = if (definition is HybridNetwork) {
                                result.appendln(".PHONY: $deliminatedFolder/$deliminatedName.a")
                                result.appendln("$deliminatedFolder/$deliminatedName.a:")
                                result.appendln("\t@\$(MAKE) -C $deliminatedFolder/ $deliminatedName.a")
                                result.appendln()

                                "$deliminatedFolder/$deliminatedName.a"
                            } else {
                                // Create the compile command for the file
                                result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedFolder/$deliminatedName.c"), listOf("$deliminatedFolder/$deliminatedName.h", "\$(BASEDIR)/${CCodeGenerator.CONFIG_FILE}")))
                                result.appendln()

                                "\$(OBJECTSDIR)/$deliminatedName.o"
                            }

                            // Keep track of the sources
                            sources.add(dependency)
                        }
                    }
                }
                else {
                    // We only want to generate each definition once, so keep a track of them
                    val generated = ArrayList<UUID>()
                    for((_, instance) in item.getAllInstances()) {
                        val instantiate = item.getInstantiateForInstantiateId(instance.instantiate, true)
                        if (instantiate != null) {
                            // Check if we've seen this type before
                            if (!generated.contains(instantiate.definition)) {
                                // If we haven't seen it, keep track of it
                                generated.add(instantiate.definition)

                                // Generate the file name that we'll be looking for
                                val deliminatedName = Utils.createFileName(instantiate.name)

                                // Create the compile command for the file
                                result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedName.c"), listOf("$deliminatedName.h", "\$(BASEDIR)/${CCodeGenerator.CONFIG_FILE}")))
                                result.appendln()

                                // Keep track of the sources
                                sources.add("\$(OBJECTSDIR)/$deliminatedName.o")
                            }
                        }
                    }
                }
            }

            // Generate the file name for the main file of this network
            val deliminatedName = Utils.createFileName(item.name)

            // Create the compile command for it
            result.append(generateCompileCommand(deliminatedName, listOf("$deliminatedName.c"), listOf("$deliminatedName.h", "\$(BASEDIR)/${CCodeGenerator.CONFIG_FILE}")))
            result.appendln()

            // Keep track of the sources
            sources.add("\$(OBJECTSDIR)/$deliminatedName.o")

            // Generate the archive command, with all the sources
            result.append(generateArchiveCommand("\$(TARGET)", sources))
            result.appendln()
        }


        // Now we have the clean command, which deletes the target and all objects
        result.appendln(".PHONY: clean")
        result.appendln("clean:")
        result.appendln("\t@echo Removing compiled binaries...")
        result.appendln("\t@rm -rf \$(TARGET) \$(OBJECTSDIR)/* *~")
        for(source in sources.filter({it.endsWith(".a") && it.contains("/")})) {
            result.appendln("\t@\$(MAKE) -C ${source.substring(0, source.lastIndexOf("/"))}/ clean")
        }
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
        result.append("\$(OBJECTSDIR)/$name.o:")
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
        result.appendln("\t@mkdir -p \$(OBJECTSDIR)")
        result.append("\t@\$(CC) \$(CFLAGS)")
        if(sources.size == 1) {
            // If there's only one source then we use the shorthand for Makefile sources
            result.append(" \$<")
        }
        else {
            // Otherwise let's just list them all
            for (source in sources) {
                result.append(" $source")
            }
        }
        // The output also uses the Makefile shorthand
        result.appendln(" -o \$@")

        // Return the compile command
        return result.toString()
    }

    /**
     * Generates an archive command for the program, taking in the given sources
     */
    private fun generateArchiveCommand(output: String, sources: List<String>): String {
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

        // If we have any sub-libraries we need to include, let's get them
        if(sources.any({it.endsWith(".a")})) {
            result.appendln("\t@mkdir -p \$(OBJECTSDIR)")
            // Iterate over every library and extract
            for(source in sources.filter({it.endsWith(".a")}))
                result.appendln("\t@cd \$(OBJECTSDIR) && \$(AR) \$(AREXTRACT) ../$source")
        }

        // And add the archive command
        result.appendln("\t@rm -f \$(TARGET)")
        result.appendln("\t@\$(AR) \$(ARFLAGS) \$(TARGET) \$(OBJECTSDIR)/*.o")

        // Return the linker command
        return result.toString()
    }
}